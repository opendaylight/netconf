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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler.PriorKnowledgeUpgradeEvent;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.util.AsciiString;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supported HTTP URI schemes.
 */
public enum HTTPScheme {
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#section-4.2.1">http scheme</a>.
     */
    HTTP(HttpScheme.HTTP) {
        private static final Http2FrameLogger FRAME_LOGGER = new Http2FrameLogger(LogLevel.DEBUG, "Clear2To1");

        @Override
        void initializeServerPipeline(final ChannelHandlerContext ctx, final Uint32 frameSize,
                final Uint32 maxInitialLineLength, final Uint32 maxHeaderSize, final Uint32 maxRequestChunkSize,
                final Uint32 maxRequestBodySize) {
            // Cleartext upgrade flow
            final var sourceCodec = new HttpServerCodec(maxInitialLineLength.intValue(), maxHeaderSize.intValue(),
                maxRequestChunkSize.intValue());
            final var http2FrameCodec = newHttp2FrameCodec(FRAME_LOGGER, frameSize, maxHeaderSize);
            ctx.pipeline()
                .addBefore(ctx.name(), null, new CleartextHttp2ServerUpgradeHandler(
                    sourceCodec,
                    new HttpServerUpgradeHandler(
                        sourceCodec,
                        protocol -> {
                            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                                return new Http2ServerUpgradeCodec(http2FrameCodec, new UpgradeMultiplexHandler());
                            }
                            return null;
                        },
                        maxRequestBodySize.intValue()),
                    http2FrameCodec))
                .addBefore(ctx.name(), null, new CleartextUpgradeHandler(maxRequestBodySize));
        }
    },
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#section-4.2.2">https scheme</a>.
     */
    HTTPS(HttpScheme.HTTPS) {
        @Override
        void initializeServerPipeline(final ChannelHandlerContext ctx, final Uint32 frameSize,
                final Uint32 maxInitialLineLength, final Uint32 maxHeaderSize, final Uint32 maxRequestChunkSize,
                final Uint32 maxRequestBodySize) {
            ctx.pipeline().addBefore(ctx.name(), null, new AlpnUpgradeHandler(frameSize, maxInitialLineLength,
                maxHeaderSize, maxRequestChunkSize, maxRequestBodySize));
        }
    };

    /**
     * Application-Level Protocol Negotiation-based channel pipeline configurator.
     */
    private static final class AlpnUpgradeHandler extends ApplicationProtocolNegotiationHandler {
        private static final Logger LOG = LoggerFactory.getLogger(AlpnUpgradeHandler.class);
        private static final Http2FrameLogger FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Alpn2To1");

        private final Uint32 frameSize;
        private final Uint32 maxInitialLineLength;
        private final Uint32 maxHeaderSize;
        private final Uint32 maxRequestChunkSize;
        private final Uint32 maxRequestBodySize;

        AlpnUpgradeHandler(final Uint32 frameSize, final Uint32 maxInitialLineLength, final Uint32 maxHeaderSize,
                final Uint32 maxRequestChunkSize, final Uint32 maxRequestBodySize) {
            super(ApplicationProtocolNames.HTTP_1_1);
            this.frameSize = requireNonNull(frameSize);
            this.maxInitialLineLength = requireNonNull(maxInitialLineLength);
            this.maxHeaderSize = requireNonNull(maxHeaderSize);
            this.maxRequestChunkSize = requireNonNull(maxRequestChunkSize);
            this.maxRequestBodySize = requireNonNull(maxRequestBodySize);
        }

        @Override
        protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
            switch (protocol) {
                case null -> throw new NullPointerException();
                case ApplicationProtocolNames.HTTP_1_1 -> configureHttp1(ctx);
                case ApplicationProtocolNames.HTTP_2 -> configureHttp2(ctx);
                default -> throw new IllegalStateException("unknown protocol: " + protocol);
            }
        }

        private void configureHttp1(final ChannelHandlerContext ctx) {
            LOG.debug("{}: using HTTP/1.1", ctx.channel());
            ctx.pipeline()
                .addAfter(ctx.name(), null, new HttpObjectAggregator(maxRequestBodySize.intValue()))
                .addAfter(ctx.name(), null, new HttpServerKeepAliveHandler())
                .replace(this, null, new HttpServerCodec(maxInitialLineLength.intValue(), maxHeaderSize.intValue(),
                    maxRequestChunkSize.intValue()));
            ctx.fireUserEventTriggered(HTTPServerPipelineSetup.HTTP_11);
        }

        private void configureHttp2(final ChannelHandlerContext ctx) {
            LOG.debug("{}: using HTTP/2", ctx.channel());
            ctx.pipeline().replace(this, "h2-frame-codec", newHttp2FrameCodec(FRAME_LOGGER, frameSize, maxHeaderSize));
            ctx.fireUserEventTriggered(HTTPServerPipelineSetup.HTTP_2);
        }
    }

    /**
     * Internal h2c upgrade hook used to notify {@link HTTPServerSessionBootstrap} that HTTP/2 setup
     * needs to be completed before the upgraded request is replayed on stream 1.
     *
     * <p>This handler is installed temporarily as part of {@link Http2ServerUpgradeCodec}. Once added
     * to the pipeline, it emits {@link HTTPServerPipelineSetup#HTTP_2} and immediately removes itself.
     */
    private static final class UpgradeMultiplexHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            ctx.fireUserEventTriggered(HTTPServerPipelineSetup.HTTP_2);
            ctx.pipeline().remove(this);
        }
    }

    /**
     * Cleartext-based channel pipeline configurator.
     */
    private static final class CleartextUpgradeHandler extends SimpleChannelInboundHandler<HttpMessage> {
        private static final Logger LOG = LoggerFactory.getLogger(CleartextUpgradeHandler.class);

        private final Uint32 maxRequestBodySize;

        CleartextUpgradeHandler(final Uint32 maxRequestBodySize) {
            super(HttpMessage.class, false);
            this.maxRequestBodySize = requireNonNull(maxRequestBodySize);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final HttpMessage request) {
            // if there was no upgrade to HTTP/2, the incoming message is accepted via channelRead();
            // configure HTTP/1.1 flow, pass the message further the pipeline, remove self as no longer required
            LOG.debug("{}: continuing with HTTP/1.1", ctx.channel());
            ctx.pipeline()
                .addAfter(ctx.name(), null, new HttpObjectAggregator(maxRequestBodySize.intValue()))
                .replace(this, null, new HttpServerKeepAliveHandler());
            ctx.fireUserEventTriggered(HTTPServerPipelineSetup.HTTP_11);
            ctx.fireChannelRead(request);
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
            // if there was an upgrade to HTTP/2, the incoming message is propagated as an UpgradeEvent;
            // just pass the request down on the dedicated HTTP/2 stream. Since we are restoring that magic, there is no
            // need for downstream handlers to see this event.
            switch (event) {
                case HttpServerUpgradeHandler.UpgradeEvent unused -> {
                    LOG.debug("{}: upgraded to HTTP/2", ctx.channel());
                    ctx.pipeline().remove(this);
                    ctx.fireUserEventTriggered(HTTPServerPipelineSetup.HTTP_2);
                }
                case PriorKnowledgeUpgradeEvent unused -> {
                    // Prior-knowledge h2: CleartextHttp2ServerUpgradeHandler has already installed Http2FrameCodec,
                    LOG.debug("{}: using HTTP/2 prior knowledge", ctx.channel());
                    ctx.pipeline().remove(this);
                    ctx.fireUserEventTriggered(HTTPServerPipelineSetup.HTTP_2);
                }
                case null, default -> super.userEventTriggered(ctx, event);
            }
        }
    }

    private final @NonNull HttpScheme netty;

    HTTPScheme(final HttpScheme netty) {
        this.netty = requireNonNull(netty);
    }

    /**
     * Returns the corresponding Netty {@link HttpScheme}.
     *
     * @return the corresponding Netty {@link HttpScheme}
     */
    public final @NonNull HttpScheme netty() {
        return netty;
    }

    /**
     * Format a host string into the corresponding URI.
     *
     * @param host host string
     * @return URI pointing to the string
     * @throws URISyntaxException when {@code host} includes a user info block, i.e. violates
     *         <a href="https://www.rfc-editor.org/rfc/rfc9110#section-4.2.4">RFC9110</a>
     */
    public final @NonNull URI hostUriOf(final String host) throws URISyntaxException {
        final var ret = new URI(toString(), host, null, null, null).parseServerAuthority();
        if (ret.getUserInfo() != null) {
            throw new URISyntaxException(host, "Host contains userinfo");
        }
        return ret;
    }

    /**
     * Initialize a pipeline so that specified {@link ChannelHandlerContext} observes {@link FullHttpMessage}s.
     *
     * @param ctx reference {@link ChannelHandlerContext}
     * @param frameSize maximum HTTP/2 frame size
     * @param maxInitialLineLength maximum HTTP/1.1 request line length
     * @param maxHeaderSize maximum HTTP request header size
     * @param maxRequestChunkSize maximum HTTP/1.1 decoder chunk size
     * @param maxRequestBodySize maximum aggregated request body size
     */
    abstract void initializeServerPipeline(ChannelHandlerContext ctx, Uint32 frameSize, Uint32 maxInitialLineLength,
            Uint32 maxHeaderSize, Uint32 maxRequestChunkSize, Uint32 maxRequestBodySize);

    @Override
    public String toString() {
        return netty.toString();
    }

    private static Http2FrameCodec newHttp2FrameCodec(final Http2FrameLogger frameLogger, final Uint32 frameSize,
            final Uint32 maxHeaderSize) {
        final var settings = Http2Settings.defaultSettings().maxHeaderListSize(maxHeaderSize.longValue())
            .maxFrameSize(frameSize.intValue());
        return Http2FrameCodecBuilder.forServer()
            .initialSettings(settings)
            .frameLogger(frameLogger)
            .gracefulShutdownTimeoutMillis(0L)
            .build();
    }
}
