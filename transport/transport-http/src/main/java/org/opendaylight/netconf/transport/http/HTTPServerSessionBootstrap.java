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
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap for a {@link HTTPServerSession}.
 */
public abstract class HTTPServerSessionBootstrap extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(HTTPServerSessionBootstrap.class);

    protected final @NonNull HTTPScheme scheme;

    protected HTTPServerSessionBootstrap(final HTTPScheme scheme) {
        this.scheme = requireNonNull(scheme);
    }

    @Override
    public final void handlerAdded(final ChannelHandlerContext ctx) {
        scheme.initializeServerPipeline(ctx);
    }

    @Override
    public final void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
        final HttpServerCodec sourceCodec = new HttpServerCodec();
        final var frameListener = configureHttp2(ctx);
        final HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(sourceCodec, protocol -> {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                return new Http2ServerUpgradeCodec(new Http2ConnectionHandlerBuilder()
                    .frameListener(frameListener).build());
            } else {
                return null;
            }
        });

        if (event instanceof HTTPServerPipelineSetup setup) {
            LOG.debug("{} resolved to {} semantics", ctx.channel(), setup);
            ctx.pipeline().replace(this, null, switch (setup) {
                case HTTP_11 -> configureHttp1(ctx);
                case HTTP_2 -> new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, frameListener);
            });
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
     * Configure the pipeline to receive HTTP/2 concurrent traffic and return the replacement handler.
     *
     * @param ctx the {@link ChannelHandlerContext} of this handler
     * @return replacement {@link ChannelInboundHandler}
     */
    @NonNullByDefault
    protected abstract ConcurrentHTTPServerSession configureHttp2(ChannelHandlerContext ctx) ;
}