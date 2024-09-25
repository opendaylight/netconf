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
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.AsciiString;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.opendaylight.restconf.server.api.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
final class RestconfSession extends SimpleChannelInboundHandler<FullHttpRequest> implements TransportSession {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfSession.class);
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();
    private static final Set<HttpMethod> IMPLEMENTED_METHODS = Set.of(
        HttpMethod.DELETE, HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.PATCH, HttpMethod.POST,
        HttpMethod.PUT);

    private final RestconfRequestDispatcher dispatcher;
    private final WellKnownResources wellKnown;

    RestconfSession(final WellKnownResources wellKnown, final RestconfRequestDispatcher dispatcher) {
        super(FullHttpRequest.class, false);
        this.wellKnown = requireNonNull(wellKnown);
        this.dispatcher = requireNonNull(dispatcher);
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
        // - HTTP/1.1 protocol: if there is none, or if there are multiple values, it is a 400 Bad Request, as per
        //   https://www.rfc-editor.org/rfc/rfc9112#section-3.2
        final var hostItr = headers.valueStringIterator(HttpHeaderNames.HOST);
        if (!hostItr.hasNext()) {
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        final var host = hostItr.next();
        if (hostItr.hasNext()) {
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // next up:
        // - check if we implement requested method
        // - we do NOT implement CONNECT method, which is the only valid use of URIs in authority-form
        final var method = msg.method();
        if (!IMPLEMENTED_METHODS.contains(method)) {
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
        final URI parsedUri;
        try {
            parsedUri = new URI(uri);
        } catch (URISyntaxException e) {
            LOG.debug("Invalid request URI '{}'", uri, e);
            msg.release();
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // now let's see what's what, according to https://www.rfc-editor.org/rfc/rfc2396#appendix-A we have:
        //        URI-reference = [ absoluteURI | relativeURI ] [ "#" fragment ]
        //        absoluteURI   = scheme ":" ( hier_part | opaque_part )
        //        relativeURI   = ( net_path | abs_path | rel_path ) [ "?" query ]
        // therefore if we have a schema, we know we have absolute-form, is also the Target Uri
        final URI targetUri;
        if (parsedUri.getScheme() != null) {
            // we do not have a schema, it is the relativeURI case, from which we only support abs_path, so let's
            // resolve it against the Host-derived URI
            final URI hostUri;
            try {
                // FIXME: establish the scheme
                hostUri = new URI(HttpScheme.HTTPS.toString(), host, null, null, null);
            } catch (URISyntaxException e) {
                LOG.debug("Invalid request Host '{}'", host, e);
                msg.release();
                respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST));
                return;
            }

            targetUri = hostUri.resolve(parsedUri);
        } else {
            targetUri = parsedUri;
        }

        final var decoder = new QueryStringDecoder(targetUri);
        final var path = decoder.path();

        if (path.startsWith("/.well-known/")) {
            // Well-known resources are immediately available and are trivial to service
            respond(ctx, streamId, wellKnown.request(version, msg.method(), path.substring(13)));
            msg.release();
        } else {
            // Defer to dispatcher
            dispatchRequest(ctx, version, streamId, decoder, msg);
        }
    }

    @VisibleForTesting
    static FullHttpResponse asteriskRequest(final HttpVersion version, final HttpMethod method) {
        if (HttpMethod.OPTIONS.equals(method)) {
            final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.ALLOW, "DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT");
            return response;
        }
        return new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_REQUEST);
    }

    private void dispatchRequest(final ChannelHandlerContext ctx, final HttpVersion version, final Integer streamId,
            final QueryStringDecoder decoder, final FullHttpRequest msg) {
        dispatcher.dispatch(decoder, msg, new RestconfRequest() {
            @Override
            public void onSuccess(final FullHttpResponse response) {
                msg.release();
                respond(ctx, streamId, response);
            }
        });
    }

    private static void respond(final ChannelHandlerContext ctx, final Integer streamId,
            final FullHttpResponse response) {
        if (streamId != null) {
            response.headers().setInt(STREAM_ID, streamId);
        }
        ctx.writeAndFlush(response);
    }
}
