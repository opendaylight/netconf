/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.AsciiString;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
final class RestconfSession extends SimpleChannelInboundHandler<FullHttpRequest> implements TransportSession {
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();

    private final RestconfRequestDispatcher dispatcher;
    private final WellKnownResources wellKnown;

    RestconfSession(final WellKnownResources wellKnown, final RestconfRequestDispatcher dispatcher) {
        super(FullHttpRequest.class, false);
        this.wellKnown = requireNonNull(wellKnown);
        this.dispatcher = requireNonNull(dispatcher);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        // non-null indicates HTTP/2 request, which we need to propagate to any response
        final var streamId = msg.headers().getInt(STREAM_ID);
        final var version = msg.protocolVersion();
        final var decoder = new QueryStringDecoder(msg.uri());
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
