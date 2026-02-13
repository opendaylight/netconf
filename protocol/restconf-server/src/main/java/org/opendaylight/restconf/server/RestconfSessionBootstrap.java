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
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap;
import org.opendaylight.netconf.transport.http.PipelinedHTTPServerSession;
import org.opendaylight.yangtools.yang.common.Uint32;

@NonNullByDefault
final class RestconfSessionBootstrap extends HTTPServerSessionBootstrap {
    private static final int MAX_HTTP2_CONTENT_LENGTH = 16 * 1024;

    private final EndpointRoot root;
    private final Uint32 chunkSize;

    RestconfSessionBootstrap(final HTTPScheme scheme, final EndpointRoot root,
            final Uint32 chunkSize, final Uint32 frameSize) {
        super(scheme, frameSize);
        this.root = requireNonNull(root);
        this.chunkSize = requireNonNull(chunkSize);
    }

    @Override
    protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext ctx) {
        return new RestconfSession(scheme, ctx.channel().remoteAddress(), root, chunkSize);
    }

    @Override
    protected ChannelInitializer<Channel> configureHttp2(final ChannelHandlerContext ctx) {
        return new ChannelInitializer<>() {
            @Override protected void initChannel(final Channel ch) {
                final var pipeline = ch.pipeline();
                pipeline.addLast(new Http2StreamFrameToHttpObjectCodec(true));
                pipeline.addLast(new HttpObjectAggregator(MAX_HTTP2_CONTENT_LENGTH));
                pipeline.addLast("restconf-session", new ConcurrentRestconfSession(scheme,
                    ctx.channel().remoteAddress(), root, chunkSize));
            }
        };
    }
}
