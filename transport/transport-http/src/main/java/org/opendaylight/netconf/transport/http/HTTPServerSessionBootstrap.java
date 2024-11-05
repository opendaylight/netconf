/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http2.Http2Settings;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The initial handler receiving the first request the client makes. This is needed to support HTTP/1.1 -> HTTP/2
 * upgrade, for which we need to see the upgrade header.
 */
public abstract class HTTPServerSessionBootstrap extends ChannelInboundHandlerAdapter {
    protected final @NonNull HttpScheme scheme;

    protected HTTPServerSessionBootstrap(final HttpScheme scheme) {
        this.scheme = requireNonNull(scheme);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        switch (msg) {
            // HTTP/1 request message: there is no upgrade, client is HTTP/1.1
            case HttpMessage message -> switchToHttp1Session(ctx, message);
            // HTTP/2 session settings: client is HTTP/2
            case Http2Settings settings -> switchToHttp2Session(ctx, settings);
            default -> {
                // no-op
            }
        }
        super.channelRead(ctx, msg);
    }

    @NonNullByDefault
    private void switchToHttp1Session(final ChannelHandlerContext ctx, final HttpMessage message) {
        // weird way of going from:
        // ... -> this handler -> ...
        // to:
        // ... -> HttpServerKeepAliveHandler -> [HttpObjectAggregator ->] session -> ...
        final var pipeline = ctx.pipeline();
        final var session = createHttp1Session();
        if (session.needsAggregator()) {
            pipeline.addAfter(ctx.name(), null, new HttpObjectAggregator(HTTPServer.MAX_HTTP_CONTENT_LENGTH));
        }
        pipeline.addAfter(ctx.name(), null, session).replace(this, null, new HttpServerKeepAliveHandler());
    }

    @NonNullByDefault
    private void switchToHttp2Session(final ChannelHandlerContext ctx, final Http2Settings settings) {
        ctx.pipeline().replace(this, null, createHttp2Session(settings));
    }

    @NonNullByDefault
    protected abstract HTTPServerSession createHttp1Session();

    // FIXME: not using HTTPServerSession, but Http2ServerSession
    @NonNullByDefault
    protected abstract HTTPServerSession createHttp2Session(Http2Settings settings);
}
