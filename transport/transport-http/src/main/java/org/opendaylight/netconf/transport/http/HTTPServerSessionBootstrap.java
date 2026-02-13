/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap for a {@link HTTPServerSession}.
 */
public abstract class HTTPServerSessionBootstrap extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(HTTPServerSessionBootstrap.class);

    protected final @NonNull HTTPScheme scheme;
    protected final @NonNull Uint32 frameSize;

    protected HTTPServerSessionBootstrap(final HTTPScheme scheme, final Uint32 frameSize) {
        this.scheme = requireNonNull(scheme);
        this.frameSize = frameSize;
    }

    @Override
    public final void handlerAdded(final ChannelHandlerContext ctx) {
        scheme.initializeServerPipeline(ctx, frameSize);
    }

    @SuppressWarnings("checkstyle:MissingSwitchDefault")
    @Override
    public final void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
        if (event instanceof HTTPServerPipelineSetup setup) {
            LOG.debug("{} resolved to {} semantics", ctx.channel(), setup);
            switch (setup) {
                case HTTP_11 -> ctx.pipeline().replace(this, null, configureHttp1(ctx));
                case HTTP_2 -> ctx.pipeline().replace(this, null, new Http2MultiplexHandler(
                    new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            ch.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(true));
                            ch.pipeline().addLast(new HttpObjectAggregator(HTTPServer.MAX_HTTP_CONTENT_LENGTH));
                        }
                    },
                    configureHttp2(ctx)));
            }
        } else {
            super.userEventTriggered(ctx, event);
        }
    }

    /**
     * Configure the pipeline to receive HTTP/1.1 pipelined traffic and return the replacement handler.
     *
     * @param ctx the {@link ChannelHandlerContext} of this handler
     * @return replacement {@link ChannelInboundHandler}
     */
    @NonNullByDefault
    protected abstract PipelinedHTTPServerSession configureHttp1(ChannelHandlerContext ctx);

    /**
     * Build the per-stream initializer the HTTP/2 pipeline should install into {@link Http2MultiplexHandler}.
     *
     * @param ctx the {@link ChannelHandlerContext} associated with the parent HTTP/2 connection
     * @return channel initializer configuring stream channels
     */
    @NonNullByDefault
    protected abstract ChannelInitializer<Channel> configureHttp2(ChannelHandlerContext ctx);
}
