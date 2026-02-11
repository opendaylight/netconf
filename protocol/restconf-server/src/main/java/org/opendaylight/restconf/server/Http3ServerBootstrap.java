/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.spi.NettyTransportSupport;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Http3ServerBootstrap implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Http3ServerBootstrap.class);

    private final @NonNull Channel channel;
    private final EventLoopGroup quicGroup;

    private Http3ServerBootstrap(final Channel channel, final EventLoopGroup quicGroup) {
        this.channel = requireNonNull(channel);
        this.quicGroup = requireNonNull(quicGroup);
    }

    static @Nullable Http3ServerBootstrap start(final String bindAddress, final int bindPort,
            final X509Certificate certificate, final PrivateKey privateKey, final EndpointRoot root,
            final Uint32 chunkSize, final int maxContentLength) throws SSLException {
        final var sslContext = QuicSslContextBuilder.forServer(privateKey, null, certificate)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        final var streamInitializer = new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(final QuicStreamChannel stream) {
                final var pipeline = stream.pipeline();
                pipeline.addLast(new Http3FrameToHttpObjectCodec(true));
                pipeline.addLast(new HttpObjectAggregator(maxContentLength));
                pipeline.addLast("restconf-session", new ConcurrentRestconfSession(HTTPScheme.HTTPS,
                    stream.parent().remoteAddress(), root, chunkSize));
            }
        };

        final var codec = Http3.newQuicServerCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
            .initialMaxData(10000000)
            .initialMaxStreamDataBidirectionalLocal(1000000)
            .initialMaxStreamDataBidirectionalRemote(1000000)
            .initialMaxStreamsBidirectional(100)
            .handler(new ChannelInitializer<QuicChannel>() {
                @Override
                protected void initChannel(final QuicChannel ch) {
                    ch.pipeline().addLast(new Http3ServerConnectionHandler(streamInitializer));
                }
            })
            .build();

        final var group = NettyTransportSupport.newEventLoopGroup(0,
            Thread.ofPlatform()
                .name("restconf-http3-", 0)
                .uncaughtExceptionHandler((thread, ex) ->
                    LOG.error("Thread terminated due to uncaught exception: {}", thread.getName(), ex))
                .factory());

        final var bootstrap = NettyTransportSupport.newDatagramBootstrap()
            .group(group)
            .handler(codec);

        final var channel = bootstrap
            .bind(new InetSocketAddress(bindAddress, bindPort))
            .syncUninterruptibly()
            .channel();

        LOG.info("HTTP/3 listener bound on {}:{}", bindAddress, bindPort);
        return new Http3ServerBootstrap(channel, group);
    }

    @Override
    public void close() {
        channel.close();
        quicGroup.shutdownGracefully();
    }
}
