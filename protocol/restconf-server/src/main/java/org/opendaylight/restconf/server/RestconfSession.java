/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
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
import org.opendaylight.restconf.server.api.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
// FIXME: HTTP/1.1 and HTTP/2 behave differently w.r.t. incoming requests and their servicing:
//        1. HTTP/1.1 uses pipelining, therefore:
//           - we cannot halt incoming pipeline
//           - we may run request prepare() while a request is executing in the background, but
//           - we MUST send responses out in the same order we have received them
//           - SSE GET turns the session into a sender, i.e. no new requests can be processed
//        2. HTTP/2 uses concurrent execution, therefore
//           - we need to track which streams are alive and support terminating pruning requests when client resets
//             a stream
//           - SSE is nothing special
//           - we have Http2Settings, which has framesize -- which we should use when streaming responses
//        We support HTTP/1.1 -> HTTP/2 upgrade for the first request only -- hence we know before processing the first
//        result which mode of operation is effective. We probably need to have two subclasses of this thing, with
//        HTTP/1.1 and HTTP/2 specializations.
final class RestconfSession extends SimpleChannelInboundHandler<FullHttpRequest> implements TransportSession {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfSession.class);
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();
    private static final Map<HttpMethod, ImplementedMethod> IMPLEMENTED_METHODS =
        Arrays.stream(ImplementedMethod.values())
            .collect(Collectors.toUnmodifiableMap(ImplementedMethod::httpMethod, Function.identity()));

    private final HttpScheme scheme;
    private final EndpointRoot root;

    RestconfSession(final HttpScheme scheme, final EndpointRoot root) {
        super(FullHttpRequest.class, false);
        this.scheme = requireNonNull(scheme);
        this.root = requireNonNull(root);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
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
        final var method = IMPLEMENTED_METHODS.get(nettyMethod);
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

        switch (root.prepare(this, method, targetUri, msg)) {
            case CompletedRequest completed -> {
                msg.release();
                respond(ctx, streamId, completed.toHttpResponse(version));
            }
            case PendingRequest<?> pending -> {
                LOG.debug("Dispatching {} {}", method, targetUri);
                executeRequest(ctx, version, streamId, pending, msg.content());
            }
        }
    }

    // FIXME: NETCONF-1379: second part of integration here:
    //        - we will have PendingRequest<?>, which is the asynchronous invocation
    //        - add a new field to track them:
    //          ConcurrentMap<PendingRequest<?>, RequestContext> executingRequests;
    //        - RequestContext is a DTO that holds streamId, ctx, msg (maybe) and perhaps some more state as needed
    //        - this class implements PendingRequestListener:
    //          - when request{Completed,Failed} is invoked, perform executingRequests.remove(req) to get
    //            the corresponding RequestContext
    //          - use that to call respond() with a formatted response (for now)
    //
    // TODO: We are entering here owning a 'content' reference, which we'll need to relinquish eventually. The way we
    //       should tackle that is forwarding an ByteBufOutputStream with release-on-close set.
    private static void executeRequest(final ChannelHandlerContext ctx, final HttpVersion version,
            final Integer streamId, final PendingRequest<?> pending, final ByteBuf content) {
        new RestconfRequest() {
            @Override
            public void onSuccess(final FullHttpResponse response) {
                content.release();
                respond(ctx, streamId, response);
            }
        }.execute(pending, version, content);
    }

    @VisibleForTesting
    static @NonNull FullHttpResponse asteriskRequest(final HttpVersion version, final ImplementedMethod method) {
        if (method == ImplementedMethod.OPTIONS) {
            final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.ALLOW, "DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT");
            return response;
        }
        LOG.debug("Invalid use of '*' with method {}", method);
        return new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST);
    }

    @VisibleForTesting
    static @NonNull URI hostUriOf(final HttpScheme scheme, final String host) throws URISyntaxException {
        final var ret = new URI(scheme.toString(), host, null, null, null).parseServerAuthority();
        if (ret.getUserInfo() != null) {
            throw new URISyntaxException(host, "Illegal Host header");
        }
        return ret;
    }

    private static void respond(final ChannelHandlerContext ctx, final Integer streamId,
            final FullHttpResponse response) {
        if (streamId != null) {
            response.headers().setInt(STREAM_ID, streamId);
        }
        ctx.writeAndFlush(response);
    }
}
