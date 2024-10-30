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
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
 */
@Beta
public abstract class HTTPServerSession extends SimpleChannelInboundHandler<FullHttpRequest>
        implements PendingRequestListener {
    /**
     * Transport-level details about a {@link PendingRequest} execution.
     *
     * @param ctx the {@link ChannelHandlerContext} on which the request is occuring
     * @param streamId the HTTP/2 stream ID, if present
     * @param version HTTP version of the request
     */
    @NonNullByDefault
    private record RequestContext(ChannelHandlerContext ctx, HttpVersion version, @Nullable Integer streamId) {
        RequestContext {
            requireNonNull(ctx);
            requireNonNull(version);
        }
    }

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

    private final ConcurrentMap<PendingRequest<?>, RequestContext> executingRequests = new ConcurrentHashMap<>();
    private final HttpScheme scheme;

    protected HTTPServerSession(final HttpScheme scheme) {
        super(FullHttpRequest.class, false);
        this.scheme = requireNonNull(scheme);
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
            hostUri = hostUriOf(scheme, host);
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
        // BUT but we do not implement the CONNECT method, so let's enlist URI's services first
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

        switch (prepareRequest(method, targetUri, msg.headers())) {
            case CompletedRequest completed -> {
                msg.release();
                respond(ctx, streamId, version, completed.asResponse());
            }
            case PendingRequest<?> pending -> {
                LOG.debug("Dispatching {} {}", method, targetUri);
                executeRequest(new RequestContext(ctx, version, streamId), pending, msg.content());
            }
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

    @VisibleForTesting
    static final @NonNull URI hostUriOf(final HttpScheme scheme, final String host) throws URISyntaxException {
        final var ret = new URI(scheme.toString(), host, null, null, null).parseServerAuthority();
        if (ret.getUserInfo() != null) {
            throw new URISyntaxException(host, "Illegal Host header");
        }
        return ret;
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
     * to {@link #respond(ChannelHandlerContext, Integer, FullHttpResponse)} when reponding to the request.
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

    private void executeRequest(final RequestContext context, final PendingRequest<?> pending, final ByteBuf content) {
        // We are invoked with content's reference and need to make sure it gets released.
        final ByteBufInputStream body;
        if (content.isReadable()) {
            body = new ByteBufInputStream(content, true);
        } else {
            content.release();
            body = null;
        }

        // Remember metadata about the request and then execute it
        executingRequests.put(pending, context);
        pending.execute(this, body);
    }

    @Override
    public void requestComplete(final PendingRequest<?> request, final Response response) {
        final var req = executingRequests.remove(request);
        if (req != null) {
            respond(req.ctx, req.streamId, req.version, response);
        } else {
            LOG.warn("Cannot pair request {}, not sending response {}", request, response, new Throwable());
        }
    }

    @Override
    public void requestFailed(final PendingRequest<?> request, final Exception cause) {
        LOG.warn("Internal error while processing {}", request, cause);
        final var req = executingRequests.remove(request);
        if (req != null) {
            respond(req.ctx, req.streamId, formatException(cause, req.version()));
        } else {
            LOG.warn("Cannot pair request, not sending response", new Throwable());
        }
    }

    @NonNullByDefault
    private static void respond(final ChannelHandlerContext ctx, final @Nullable Integer streamId,
            final HttpVersion version, final Response response) {
        respond(ctx, streamId, switch (response) {
            case ReadyResponse ready -> ready.toHttpResponse(version);
            default -> formatResponse(response, ctx, version);
        });
    }

    @NonNullByDefault
    private static void respond(final ChannelHandlerContext ctx, final @Nullable Integer streamId,
            final FullHttpResponse response) {
        requireNonNull(response);
        if (streamId != null) {
            response.headers().setInt(STREAM_ID, streamId);
        }
        ctx.writeAndFlush(response);
    }

    // FIXME: below payloads use a synchronous dump of data into the socket. We cannot safely do that on the event loop,
    //        because a slow client would end up throttling our IO threads simply because of TCP window and similar
    //        queuing/backpressure things.
    //
    //        we really want to kick off a virtual thread to take care of that, i.e. doing its own synchronous write
    //        thing, talking to a short queue (SPSC?) of HttpObjects.
    //
    //        the event loop of each channel would be the consumer of that queue, picking them off as quickly as
    //        possible, but expecting backpressure if the amount of pending stuff goes up.
    //
    //        as for the HttpObjects: this effectively means that the OutputStreams used in the below code should be
    //        replaced with entities which perform chunking:
    //        - buffer initial stuff, so that we produce a FullHttpResponse if the payload is below
    //          256KiB (or so), i.e. producing Content-Length header and dumping the thing in one go
    //        - otherwise emit just HttpResponse with Transfer-Enconding: chunked and continue sending
    //          out chunks (of reasonable size).
    //        - finish up with a LastHttpContent

    @NonNullByDefault
    private static FullHttpResponse formatResponse(final Response response, final ChannelHandlerContext ctx,
            final HttpVersion version) {
        try {
            return response.toHttpResponse(ctx.alloc(), version);
        } catch (IOException e) {
            LOG.warn("IO error while converting formatting response", e);
            return formatException(e, version);
        }
    }

    @NonNullByDefault
    private static FullHttpResponse formatException(final Exception cause, final HttpVersion version) {
        final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        final var content = response.content();
        // Note: we are tempted to do a cause.toString() here, but we are dealing with unhandled badness here,
        //       so we do not want to be too revealing -- hence a message is all the user gets.
        ByteBufUtil.writeUtf8(content, cause.getMessage());
        HttpUtil.setContentLength(response, content.readableBytes());
        return response;
    }
}
