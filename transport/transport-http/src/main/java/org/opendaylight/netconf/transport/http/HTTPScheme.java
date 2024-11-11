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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Supported HTTP schemes.
 */
public enum HTTPScheme {
    HTTP(HttpScheme.HTTP) {
        private static final Http2FrameLogger FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Clear2To1");

        @Override
        void initializeForServer(final ChannelHandlerContext ctx) {
            // Cleartext upgrade flow
            final var sourceCodec = new HttpServerCodec();
            final var http2handler = http2toHttp1(FRAME_LOGGER);
            ctx.pipeline()
                .addBefore(ctx.name(), null, new CleartextHttp2ServerUpgradeHandler(
                    sourceCodec,
                    new HttpServerUpgradeHandler(sourceCodec,
                        protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
                            ? new Http2ServerUpgradeCodec(http2handler) : null),
                    http2handler))
                .addBefore(ctx.name(), null, new SimpleChannelInboundHandler<HttpRequest>() {
                    @Override
                    protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {
                        // if there was no upgrade to HTTP/2 the incoming message is accepted via channel read;
                        // configure HTTP 1.1 flow, pass the message further the pipeline, remove self as no longer
                        // required
                        ctx.pipeline()
                            .addAfter(ctx.name(), null, new HttpObjectAggregator(HTTPServer.MAX_HTTP_CONTENT_LENGTH))
                            .replace(this, null, new HttpServerKeepAliveHandler());
                        ctx.fireChannelRead(ReferenceCountUtil.retain(request));
                    }

                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx, final Object event)
                            throws Exception {
                        // if there was upgrade to HTTP/2 the upgrade event is fired further the pipeline;
                        // on event occurrence it's only required to complete the configuration for future requests,
                        // then remove self as no longer required
                        if (event instanceof HttpServerUpgradeHandler.UpgradeEvent) {
                            ctx.pipeline().remove(this);
                        }
                        super.userEventTriggered(ctx, event);
                    }
                });
        }
    },
    HTTPS(HttpScheme.HTTPS) {
        @Override
        void initializeForServer(final ChannelHandlerContext ctx) {
            ctx.pipeline().addLast(new AlpnHandler());
        }

        static final class AlpnHandler extends ApplicationProtocolNegotiationHandler {
            private static final Http2FrameLogger FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Alpn2To1");

            AlpnHandler() {
                super(ApplicationProtocolNames.HTTP_1_1);
            }

            @Override
            protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
                switch (protocol) {
                    case ApplicationProtocolNames.HTTP_1_1 -> ctx.pipeline()
                        .addBefore(ctx.name(), null, new HttpServerCodec())
                        .addBefore(ctx.name(), null, new HttpServerKeepAliveHandler())
                        .addBefore(ctx.name(), null, new HttpObjectAggregator(HTTPServer.MAX_HTTP_CONTENT_LENGTH));
                    case ApplicationProtocolNames.HTTP_2 -> {
                        ctx.pipeline().addBefore(ctx.name(), null, http2toHttp1(FRAME_LOGGER));
                    }
                    default -> throw new IllegalStateException("Unsupported protocol: " + protocol);
                }
            }
        }
    };

    private final @NonNull HttpScheme netty;

    HTTPScheme(final @Nullable HttpScheme netty) {
        this.netty = requireNonNull(netty);
    }

    public final @NonNull HttpScheme netty() {
        return netty;
    }

    public final @NonNull URI hostUriOf(final String host) throws URISyntaxException {
        final var ret = new URI(toString(), host, null, null, null).parseServerAuthority();
        if (ret.getUserInfo() != null) {
            throw new URISyntaxException(host, "Illegal Host header");
        }
        return ret;
    }

    @NonNullByDefault
    abstract void initializeForServer(ChannelHandlerContext ctx);

    @Override
    public String toString() {
        return netty.toString();
    }

    // External HTTP 2 to internal HTTP 1.1 adapter handler
    private static HttpToHttp2ConnectionHandler http2toHttp1(final Http2FrameLogger frameLogger) {
        final var connection = new DefaultHttp2Connection(true);
        return new HttpToHttp2ConnectionHandlerBuilder()
            .frameListener(new DelegatingDecompressorFrameListener(connection,
                new InboundHttp2ToHttpAdapterBuilder(connection)
                    .maxContentLength(HTTPServer.MAX_HTTP_CONTENT_LENGTH)
                    .propagateSettings(true)
                    .build()))
            .connection(connection)
            .frameLogger(frameLogger)
            .gracefulShutdownTimeoutMillis(0L)
            .build();
    }
}
