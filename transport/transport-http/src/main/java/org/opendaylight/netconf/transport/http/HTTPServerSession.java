/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.AsciiString;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for handling HTTP events on a {@link HTTPServer}'s channel pipeline. Users will typically add
 * a subclass onto the corresponding {@link HTTPTransportChannel} when notified through
 * {@link TransportChannelListener}.
 *
 * <p>Incoming requests are processed in four distinct stages:
 * <ol>
 *   <li>request method binding, performed on the Netty thread via {@link #implementationOf(HttpMethod)}</li>
 *   <li>request path and header binding, performed on the Netty thread via
 *       {@link #prepareRequest(ImplementedMethod, URI, HttpHeaders)}</li>
 *   <li>request execution, performed in a dedicated thread, via
 *       {@link PendingRequest#execute(PendingRequestListener, java.io.InputStream)}</li>
 *   <li>response execution, performed in another dedicated thread</li>
 * </ol>
 * This split is done to off-load request and response body construction, so that it can result in a number of messages
 * being sent down the Netty pipeline. That aspect is important when producing chunked-encoded response from a state
 * snapshot -- which is the typical use case.
 */
@Beta
public abstract sealed class HTTPServerSession extends SimpleChannelInboundHandler<FullHttpRequest>
        permits ConcurrentHTTPServerSession, PipelinedHTTPServerSession {
    private static final Logger LOG = LoggerFactory.getLogger(HTTPServerSession.class);
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();
    private static final Map<HttpMethod, ImplementedMethod> ALL_METHODS = Arrays.stream(ImplementedMethod.values())
        .collect(Collectors.toUnmodifiableMap(ImplementedMethod::httpMethod, Function.identity()));

    // FIXME: HTTP/1.1 and HTTP/2 behave differently w.r.t. incoming requests and their servicing:
    //  1. HTTP/1.1 uses pipelining, therefore:
    //     - we cannot halt incoming pipeline
    //     - we may run request prepare() while a request is executing in the background, but
    //     - we MUST send responses out in the same order we have received them
    //     - SSE GET turns the session into a sender, i.e. no new requests can be processed
    //  2. HTTP/2 uses concurrent execution, therefore
    //     - we need to track which streams are alive and support terminating pruning requests when client resets
    //       a stream
    //     - SSE is nothing special
    //     - we have Http2Settings, which has framesize -- which we should use when streaming responses
    //  We support HTTP/1.1 -> HTTP/2 upgrade for the first request only -- hence we know before processing the first
    //  result which mode of operation is effective. We probably need to have two subclasses of this thing, with
    //  HTTP/1.1 and HTTP/2 specializations.

    // FIXME: this heavily depends on the object model and is tied to HTTPServer using aggregators, so perhaps we should
    //        reconsider the design

    private final HTTPScheme scheme;

    // Only valid when the session is attached to a Channel
    private ServerRequestExecutor executor;
    private ResponseWriter responseWriter;

    protected HTTPServerSession(final HTTPScheme scheme) {
        super(FullHttpRequest.class, false);
        this.scheme = requireNonNull(scheme);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        final var channel = ctx.channel();
        executor = new ServerRequestExecutor(channel.remoteAddress().toString(), this);
        LOG.debug("Threadpools for {} started", channel);

        responseWriter = new ResponseWriter();
        ctx.pipeline().addLast("responseWriter", responseWriter);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        shutdownTreadpools(ctx);
        super.channelInactive(ctx);
    }

    @Override
    public final void handlerRemoved(final ChannelHandlerContext ctx) {
        shutdownTreadpools(ctx);
    }

    private void shutdownTreadpools(final ChannelHandlerContext ctx) {
        executor.shutdown();
        LOG.debug("Threadpools for {} shut down", ctx.channel());
    }

    @Override
    protected final void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        final var headers = msg.headers();
        // non-null indicates HTTP/2 request, which we need to propagate to any response
        final var streamId = headers.getInt(STREAM_ID);
        final var version = msg.protocolVersion();

        // first things first:
        // - HTTP semantics: we MUST have the equivalent of a Host header, as per
        //   https://www.rfc-editor.org/rfc/rfc9110#section-7.2
        // - HTTP/1.1 protocol: it is a 400 Bad Request, as per
        //   https://www.rfc-editor.org/rfc/rfc9112#section-3.2, if
        //   - there are multiple values
        //   - the value is invalid
        final var hostItr = headers.valueStringIterator(HttpHeaderNames.HOST);
        if (!hostItr.hasNext()) {
            LOG.debug("No Host header in request {}", msg);
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        final var host = hostItr.next();
        if (hostItr.hasNext()) {
            LOG.debug("Multiple Host header values in request {}", msg);
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        final URI hostUri;
        try {
            hostUri = scheme.hostUriOf(host);
        } catch (URISyntaxException e) {
            LOG.debug("Invalid Host header value '{}'", host, e);
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // next up:
        // - check if we implement requested method
        // - we do NOT implement CONNECT method, which is the only valid use of URIs in authority-form
        final var nettyMethod = msg.method();
        final var method = implementationOf(nettyMethod);
        if (method == null) {
            LOG.debug("Method {} not implemented", nettyMethod);
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_IMPLEMENTED));
            return;
        }

        // next possibility: asterisk-form, as per https://www.rfc-editor.org/rfc/rfc9112#section-3.2.4
        // it is only applicable to server-wide OPTIONS https://www.rfc-editor.org/rfc/rfc9110#section-9.3.7, which
        // should result in Allow: listing the contents of IMPLEMENTED_METHODS
        final var uri = msg.uri();
        if (HttpUtil.isAsteriskForm(uri)) {
            msg.release();
            respond(ctx, streamId, asteriskRequest(version, method));
            return;
        }

        // we are down to three possibilities:
        // - origin-form, as per https://www.rfc-editor.org/rfc/rfc9112#section-3.2.1
        // - absolute-form, as per https://www.rfc-editor.org/rfc/rfc9112#section-3.2.2
        // - authority-form, as per https://www.rfc-editor.org/rfc/rfc9112#section-3.2.3
        // BUT we do not implement the CONNECT method, so let's enlist URI's services first
        final URI requestUri;
        try {
            requestUri = new URI(uri);
        } catch (URISyntaxException e) {
            LOG.debug("Invalid request-target '{}'", uri, e);
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // as per https://www.rfc-editor.org/rfc/rfc9112#section-3.3
        final URI targetUri;
        if (requestUri.isAbsolute()) {
            // absolute-form is the Target URI
            targetUri = requestUri;
        } else if (HttpUtil.isOriginForm(requestUri)) {
            // origin-form needs to be combined with Host header
            targetUri = hostUri.resolve(requestUri);
        } else {
            LOG.debug("Unsupported request-target '{}'", requestUri);
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        switch (tryPrepareRequest(method, targetUri, msg.headers())) {
            case CompletedRequest completed -> {
                msg.release();
                LOG.debug("Immediate response to {} {}", method, targetUri);
                executor.respond(ctx, streamId, version, completed.asResponse());
            }
            case PendingRequest<?> pending -> {
                LOG.debug("Scheduling execution of {} {}", method, targetUri);
                executor.executeRequest(ctx, version, streamId, pending, msg.content());
            }
        }
    }

    @NonNullByDefault
    @SuppressWarnings("checkstyle:illegalCatch")
    private PreparedRequest tryPrepareRequest(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        try {
            return prepareRequest(method, targetUri, headers);
        } catch (RuntimeException e) {
            LOG.warn("Unexpected error while preparing {} request to {}", method, targetUri, e);
            return new ExceptionRequestResponse(e);
        }
    }

    @VisibleForTesting
    static final @NonNull FullHttpResponse asteriskRequest(final HttpVersion version, final ImplementedMethod method) {
        if (method == ImplementedMethod.OPTIONS) {
            final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.ALLOW, "DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT");
            return response;
        }
        LOG.debug("Invalid use of '*' with method {}", method);
        return new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST);
    }

    /**
     * Check whether this session implements a particular HTTP method. Default implementation supports all
     * {@link ImplementedMethod}s.
     *
     * @param method an HTTP method
     * @return an {@link ImplementedMethod}, or {@code null} if the method is not implemented
     */
    protected @Nullable ImplementedMethod implementationOf(final @NonNull HttpMethod method) {
        return ALL_METHODS.get(method);
    }

    /**
     * Prepare an incoming HTTP request. The first two arguments are provided as context, which should be reflected back
     * to {@link #respond(ChannelHandlerContext, Integer, HttpResponse)} when responding to the request.
     *
     * <p>The ownership of request body is transferred to the implementation of this method. It is its responsibility to
     * {@link ByteBuf#release()} it when no longer needed.
     *
     * @param method {@link ImplementedMethod} being requested
     * @param targetUri URI of the target resource
     * @param headers request {@link HttpHeaders}
     */
    @NonNullByDefault
    protected abstract PreparedRequest prepareRequest(ImplementedMethod method, URI targetUri, HttpHeaders headers);

    @NonNullByDefault
    protected ChannelFuture respond(final ChannelHandlerContext ctx, final @Nullable Integer streamId,
            final HttpResponse response) {
        requireNonNull(response);
        if (streamId != null) {
            response.headers().setInt(STREAM_ID, streamId);
        }
        return ctx.writeAndFlush(response);
    }

    public ResponseWriter responseWriter() {
        return responseWriter;
    }

}
