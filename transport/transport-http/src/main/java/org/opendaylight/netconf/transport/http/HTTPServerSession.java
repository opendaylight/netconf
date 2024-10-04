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
 */
@Beta
public abstract class HTTPServerSession extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(HTTPServerSession.class);
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();
    private static final Map<HttpMethod, ImplementedMethod> ALL_METHODS = Arrays.stream(ImplementedMethod.values())
        .collect(Collectors.toUnmodifiableMap(ImplementedMethod::httpMethod, Function.identity()));

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

        processRequest(ctx, streamId, method, targetUri, version, msg.headers(), msg.content());
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
     * Process an incoming HTTP request. The first two arguments are provided as context, which should be reflected back
     * to {@link #respond(ChannelHandlerContext, Integer, FullHttpResponse)} when reponding to the request.
     *
     * <p>
     * The ownership of request body is transferred to the implementation of this method. It is its responsibility to
     * {@link ByteBuf#release()} it when no longer needed.
     *
     * @param ctx the {@link ChannelHandlerContext} on which the request is occuring
     * @param streamId the HTTP/2 stream ID, if present
     * @param method {@link ImplementedMethod} being requested
     * @param targetUri URI of the target resource
     * @param version HTTP version of the request
     * @param headers request {@link HttpHeaders}
     * @param body request body, potentially empty
     */
    @NonNullByDefault
    protected abstract void processRequest(ChannelHandlerContext ctx, @Nullable Integer streamId,
        ImplementedMethod method, URI targetUri, HttpVersion version, HttpHeaders headers, ByteBuf body);

    @NonNullByDefault
    protected static final void respond(final ChannelHandlerContext ctx, final @Nullable Integer streamId,
            final FullHttpResponse response) {
        requireNonNull(response);
        if (streamId != null) {
            response.headers().setInt(STREAM_ID, streamId);
        }
        ctx.writeAndFlush(response);
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
}
