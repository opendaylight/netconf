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
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicTransportParameters;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap;
import org.opendaylight.netconf.transport.http.PipelinedHTTPServerSession;
import org.opendaylight.yangtools.yang.common.Uint32;

@NonNullByDefault
final class RestconfSessionBootstrap extends HTTPServerSessionBootstrap {
    private static final int MAX_HTTP2_CONTENT_LENGTH = 16 * 1024;
    private static final int MAX_HTTP3_CONTENT_LENGTH = 16 * 1024;
    private static final AttributeKey<Uint32> MAX_CHUNK_SIZE = AttributeKey.valueOf(RestconfSessionBootstrap.class,
        "maxChunkSize");

    private final EndpointRoot root;
    private final Uint32 chunkSize;
    private final WriteBufferWaterMark writeBufferWaterMark;
    private final AltSvcAdvertiser altSvcAdvertiser;

    RestconfSessionBootstrap(final HTTPScheme scheme, final EndpointRoot root,
            final Uint32 chunkSize, final Uint32 frameSize, final WriteBufferWaterMark writeBufferWaterMark,
            final AltSvcAdvertiser altSvcAdvertiser) {
        super(scheme, frameSize);
        this.root = requireNonNull(root);
        this.chunkSize = requireNonNull(chunkSize);
        this.writeBufferWaterMark = requireNonNull(writeBufferWaterMark);
        this.altSvcAdvertiser = requireNonNull(altSvcAdvertiser);
    }

    @Override
    protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext ctx) {
        final var ch = ctx.channel();
        ch.config().setWriteBufferWaterMark(writeBufferWaterMark);
        ch.pipeline().addBefore(ctx.name(), null, altSvcAdvertiser);
        return new RestconfSession(scheme, ch.remoteAddress(), root, chunkSize);
    }

    @Override
    protected void configureHttp2(final ChannelHandlerContext ctx) {
        if (ctx.pipeline().context("h2-multiplexer") != null) {
            return;
        }
        final var frameCodecCtx = requireNonNull(ctx.pipeline().context(Http2FrameCodec.class));
        ctx.pipeline().addAfter(frameCodecCtx.name(), "h2-multiplexer",
            new Http2MultiplexHandler(buildHttp2ChildInitializer(ctx), buildHttp2ChildInitializer(ctx)));
        ctx.pipeline().remove(this);
    }

    @Override
    protected void configureHttp3(final ChannelHandlerContext ctx) {
        if (ctx.pipeline().context(Http3ServerConnectionHandler.class) != null) {
            return;
        }
        final var quicChannel = (QuicChannel) ctx.channel();
        quicChannel.attr(MAX_CHUNK_SIZE).set(maxChunkSize(quicChannel.peerTransportParameters(), chunkSize));
        ctx.pipeline().addLast("h3-connection", new Http3ServerConnectionHandler(buildHttp3ChildInitializer(ctx)));
        ctx.pipeline().remove(this);
    }

    private ChannelInitializer<Channel> buildHttp2ChildInitializer(final ChannelHandlerContext ctx) {
        return new ChannelInitializer<>() {
            @Override protected void initChannel(final Channel ch) {
                ch.config().setWriteBufferWaterMark(writeBufferWaterMark);
                final var pipeline = ch.pipeline();
                pipeline.addLast(new Http2StreamFrameToHttpObjectCodec(true));
                pipeline.addLast(new HttpObjectAggregator(MAX_HTTP2_CONTENT_LENGTH));
                pipeline.addLast(altSvcAdvertiser);
                pipeline.addLast("restconf-session", new ConcurrentRestconfSession(scheme,
                    ctx.channel().remoteAddress(), root, chunkSize));
            }
        };
    }

    private ChannelInitializer<QuicStreamChannel> buildHttp3ChildInitializer(final ChannelHandlerContext ctx) {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(final QuicStreamChannel ch) {
                ch.config().setWriteBufferWaterMark(writeBufferWaterMark);
                final var pipeline = ch.pipeline();
                pipeline.addLast("h3-stream-log", new LoggingHandler(LogLevel.DEBUG));
                pipeline.addLast(new Http3FrameToHttpObjectCodec(true));
                pipeline.addLast(new HttpObjectAggregator(MAX_HTTP3_CONTENT_LENGTH));
                pipeline.addLast("restconf-session", new ConcurrentRestconfSession(scheme,
                    ctx.channel().remoteAddress(), root, requireNonNull(ch.parent().attr(MAX_CHUNK_SIZE).get())));
            }
        };
    }

    /**
     * Compute an effective maximum response chunk size for an HTTP/3 connection.
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
     * @param chunkSize  configured response chunk size (application payload bytes)
     * @return effective maximum chunk size for this connection
     */
    private static Uint32 maxChunkSize(final QuicTransportParameters parameters, final Uint32 chunkSize) {
        final var peerMaxUdp = parameters != null ? parameters.maxUdpPayloadSize() : Long.MAX_VALUE;
        final var peerBound = peerMaxUdp == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(256L, peerMaxUdp - 64);
        return Uint32.valueOf(Math.min(chunkSize.longValue(), peerBound));
    }
}
