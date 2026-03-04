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
import io.netty.handler.codec.quic.QuicTransportParameters;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.spi.NettyTransportSupport;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class Http3ServerBootstrap implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Http3ServerBootstrap.class);
    private static final int MAX_HTTP3_CONTENT_LENGTH = 16 * 1024;

    private final Channel channel;
    private final EventLoopGroup quicGroup;

    private Http3ServerBootstrap(final Channel channel, final EventLoopGroup quicGroup) {
        this.channel = requireNonNull(channel);
        this.quicGroup = requireNonNull(quicGroup);
    }

    static Http3ServerBootstrap start(final String bindAddress, final int bindPort,
            final X509Certificate certificate, final PrivateKey privateKey, final EndpointRoot root,
            final Uint32 chunkSize, final Uint64 initialMaxData,
            final Uint64 initialMaxStreamDataBidirectionalRemote, final Uint32 initialMaxStreamsBidirectional)
            throws SSLException {
        final var sslContext = QuicSslContextBuilder.forServer(privateKey, null, certificate)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        final var streamInitializer = new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(final QuicStreamChannel stream) {
                final var pipeline = stream.pipeline();
                final var parameters = stream.parent().peerTransportParameters();
                pipeline.addLast("h3-stream-log", new LoggingHandler(LogLevel.DEBUG));
                pipeline.addLast(new Http3FrameToHttpObjectCodec(true));
                pipeline.addLast(new HttpObjectAggregator(MAX_HTTP3_CONTENT_LENGTH));
                pipeline.addLast("restconf-session", new ConcurrentRestconfSession(HTTPScheme.HTTPS,
                    requireNonNull(stream.parent().remoteAddress()), root,
                    getMaxChunkSize(parameters, chunkSize)));
            }
        };

        final var codec = Http3.newQuicServerCodecBuilder()
            .sslContext(sslContext)
            .initialMaxData(initialMaxData.longValue())
            .initialMaxStreamDataBidirectionalRemote(initialMaxStreamDataBidirectionalRemote.longValue())
            .initialMaxStreamsBidirectional(initialMaxStreamsBidirectional.longValue())
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
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(final Channel ch) {
                    ch.pipeline().addLast(new LoggingHandler("http3-udp", LogLevel.DEBUG));
                    ch.pipeline().addLast(codec);
                }
            });

        final var channel = bootstrap
            .bind(new InetSocketAddress(bindAddress, bindPort))
            .syncUninterruptibly()
            .channel();

        LOG.info("HTTP/3 listener bound on {}:{}", bindAddress, bindPort);
        return new Http3ServerBootstrap(channel, group);
    }

    @Override
    public void close() {
        quicGroup.shutdownGracefully();
        channel.close().syncUninterruptibly();
    }

    /**
     * Compute an effective maximum response chunk size for an HTTP/3 stream.
     *
     * <p>The peer-advertised {@code max_udp_payload_size} applies to the UDP payload on the wire, while
     * {@code chunkSize} represents application payload bytes. A fixed overhead of 64 bytes is subtracted to leave
     * headroom for QUIC/HTTP/3 framing (varint-encoded headers, STREAM/DATA framing) and the AEAD tag, plus
     * occasional coalesced control frames.
     *
     * <p>The resulting bound is clamped to a minimum of 256 bytes to avoid pathological tiny chunks when a peer
     * advertises a very small {@code max_udp_payload_size}, which would otherwise amplify per-chunk overhead and
     * increase flush pressure.
     *
     * @param parameters peer QUIC transport parameters, or {@code null} if unavailable
     * @param chunkSize configured response chunk size (application payload bytes)
     * @return effective maximum chunk size for this stream
     */
    private static Uint32 getMaxChunkSize(final @Nullable QuicTransportParameters parameters, final Uint32 chunkSize) {
        final var overhead = 64;
        final var peerMaxUdp = parameters != null ? parameters.maxUdpPayloadSize() : Long.MAX_VALUE;
        final var peerBound = peerMaxUdp == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(256L, peerMaxUdp - overhead);
        return Uint32.valueOf(Math.min(chunkSize.longValue(), peerBound));
    }
}
