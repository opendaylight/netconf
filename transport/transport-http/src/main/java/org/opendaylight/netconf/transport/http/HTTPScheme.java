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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
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

/**
 * Supported HTTP URI schemes.
 */
public enum HTTPScheme {
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#section-4.2.1">http scheme</a>.
     */
    HTTP(HttpScheme.HTTP) {
        @Override
        void initializeServerPipeline(final ChannelPipeline pipeline, final Http2ConnectionHandler connectionHandler) {
            // Cleartext upgrade flow
            final var sourceCodec = new HttpServerCodec();
            pipeline
                .addLast(new CleartextHttp2ServerUpgradeHandler(
                    sourceCodec,
                    new HttpServerUpgradeHandler(sourceCodec,
                        protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
                            ? new Http2ServerUpgradeCodec(connectionHandler) : null), connectionHandler))
                .addLast(new SimpleChannelInboundHandler<HttpMessage>() {
                    @Override
                    protected void channelRead0(final ChannelHandlerContext ctx, final HttpMessage request) {
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
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#section-4.2.2">https scheme</a>.
     */
    HTTPS(HttpScheme.HTTPS) {
        @Override
        void initializeServerPipeline(final ChannelPipeline pipeline, final Http2ConnectionHandler connectionHandler) {
            // Application protocol negotiator over TLS
            pipeline.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                @Override
                protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
                    switch (protocol) {
                        case null -> throw new NullPointerException();
                        case ApplicationProtocolNames.HTTP_1_1 -> ctx.pipeline()
                            .addAfter(ctx.name(), null, new HttpObjectAggregator(HTTPServer.MAX_HTTP_CONTENT_LENGTH))
                            .addAfter(ctx.name(), null, new HttpServerKeepAliveHandler())
                            .replace(this, null, new HttpServerCodec());
                        case ApplicationProtocolNames.HTTP_2 -> ctx.pipeline().replace(this, null, connectionHandler);
                        default -> throw new IllegalStateException("unknown protocol: " + protocol);
                    }
                }
            });
        }
    };

    private static final Http2FrameLogger FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Server");

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

    final void initializeServerPipeline(final ChannelPipeline pipeline) {
        // External HTTP 2 to internal HTTP 1.1 adapter handler
        final var connection = new DefaultHttp2Connection(true);
        final var frameListener = new InboundHttp2ToHttpAdapterBuilder(connection)
            .maxContentLength(HTTPServer.MAX_HTTP_CONTENT_LENGTH)
            .propagateSettings(true)
            .build();

        initializeServerPipeline(pipeline, new HttpToHttp2ConnectionHandlerBuilder()
            .frameListener(new DelegatingDecompressorFrameListener(connection, frameListener))
            .connection(connection)
            .frameLogger(FRAME_LOGGER)
            .gracefulShutdownTimeoutMillis(0L)
            .build());
    }

    abstract void initializeServerPipeline(ChannelPipeline pipeline, Http2ConnectionHandler connectionHandler);

    @Override
    public String toString() {
        return netty.toString();
    }
}
