/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ConcurrentHTTPServerSession;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PipelinedHTTPServerSession;
import org.opendaylight.netconf.transport.http.PreparedRequest;

@NonNullByDefault
final class RestconfSessionBootstrap extends HTTPServerSessionBootstrap {
    private final EndpointRoot root;

    RestconfSessionBootstrap(final HTTPScheme scheme, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
    }

    @Override
    protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext ctx) {
        return new RestconfSession(scheme, ctx.channel().remoteAddress(), root);
    }

    @Override
    protected ConcurrentHTTPServerSession configureHttp2(final ChannelHandlerContext ctx) {
        ctx.pipeline().addLast("h2-frame-codec", Http2FrameCodecBuilder.forServer().build());

        final var session = new ConcurrentRestconfSession(scheme, ctx.channel().remoteAddress(), root);
        ctx.pipeline().addLast("h2-multiplexer", new Http2MultiplexHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(final Channel ch) {
                // Per-stream translation + (bounded) aggregation to FullHttpRequest,
                // then auth, then our standard session logic.
                ch.pipeline().addLast("h2-httpobj", new Http2StreamFrameToHttpObjectCodec(true));

                final var auth = root.authHandlerFactory().create();
                ch.pipeline().addLast("auth", auth);

                ch.pipeline().addLast("restconf-session", session);
            }
        }));

        // Return a no-op placeholder on the parent channel. All I/O happens on child channels.
        return session;
    }
}
