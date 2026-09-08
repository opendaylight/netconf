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
import io.netty.handler.codec.http.HttpObjectDecoder;
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
    protected final int maxRequestBodySize;
    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int maxRequestChunkSize;

    protected HTTPServerSessionBootstrap(final HTTPScheme scheme, final Uint32 frameSize) {
        this(scheme, frameSize, HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH,
            HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE, HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE,
            HTTPTransportStack.MAX_HTTP_CONTENT_LENGTH);
    }

    protected HTTPServerSessionBootstrap(final HTTPScheme scheme, final Uint32 frameSize,
            final int maxInitialLineLength, final int maxHeaderSize, final int maxRequestChunkSize,
            final int maxRequestBodySize) {
        this.scheme = requireNonNull(scheme);
        this.frameSize = requireNonNull(frameSize);
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxHeaderSize = maxHeaderSize;
        this.maxRequestChunkSize = maxRequestChunkSize;
        this.maxRequestBodySize = maxRequestBodySize;
    }

    @Override
    public final void handlerAdded(final ChannelHandlerContext ctx) {
        scheme.initializeServerPipeline(ctx, frameSize, maxInitialLineLength, maxHeaderSize, maxRequestChunkSize,
            maxRequestBodySize);
    }

    @SuppressWarnings("checkstyle:MissingSwitchDefault")
    @Override
    public final void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
        if (event instanceof HTTPServerPipelineSetup setup) {
            LOG.debug("{} resolved to {} semantics", ctx.channel(), setup);
            switch (setup) {
                case HTTP_11 -> ctx.pipeline().replace(this, null, configureHttp1(ctx));
                case HTTP_2 -> configureHttp2(ctx);
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
     * Finalize the parent channel pipeline for HTTP/2 once HTTP/2 semantics have been selected.
     *
     * <p>This method is invoked after protocol detection resolves the connection to HTTP/2 via ALPN,
     * cleartext prior knowledge, or h2c upgrade. Implementations are expected to ensure the
     * {@code Http2MultiplexHandler} is installed on the parent pipeline, perform any remaining
     * connection-level HTTP/2 setup, and retire this bootstrap handler once configuration is finished.
     *
     * @param ctx the {@link ChannelHandlerContext} of this bootstrap on the parent connection channel
     */
    @NonNullByDefault
    protected abstract void configureHttp2(ChannelHandlerContext ctx);
}
