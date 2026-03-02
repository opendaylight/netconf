/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class TestHttp3 extends AbstractOpenApiHttp3Test {

    @Test
    void request() throws Exception {
        final var group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

        try {
            final var context = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(Http3.supportedApplicationProtocols()).build();
            final var codec = Http3.newQuicClientCodecBuilder()
                .sslContext(context)
                .maxIdleTimeout(50000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .build();

            final var bs = new Bootstrap();
            final var channel = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0).sync().channel();

            final var quicChannel = QuicChannel.newBootstrap(channel)
                .handler(new Http3ClientConnectionHandler())
                .remoteAddress(new InetSocketAddress(localAddress, port))
                .connect()
                .get();

            final var streamChannel = Http3.newRequestStream(quicChannel,
                new Http3RequestStreamInboundHandler() {
                    @Override
                    protected void channelRead(final ChannelHandlerContext ctx, final Http3HeadersFrame frame) {
                        ReferenceCountUtil.release(frame);
                    }

                    @Override
                    protected void channelRead(final ChannelHandlerContext ctx,  final Http3DataFrame frame) {
                        ReferenceCountUtil.release(frame);
                    }

                    @Override
                    protected void channelInputClosed(final ChannelHandlerContext ctx) {
                        ctx.close();
                    }
                }).sync().getNow();

            // Write the Header frame and send the FIN to mark the end of the request.
            // After this its not possible anymore to write any more data.
            final var frame = new DefaultHttp3HeadersFrame();
            frame.headers()
                .method("GET")
                .path("/openapi/api/v3/single")
                .authority(host)
                .scheme("https")
                .add(HttpHeaderNames.AUTHORIZATION, AsciiString.cached("Basic dXNlcm5hbWU6cGEkJHcwUmQ="));
            streamChannel.writeAndFlush(frame)
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();

            // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
            // After this is done we will close the underlying datagram channel.
            streamChannel.closeFuture().sync();

            // After we received the response lets also close the underlying QUIC channel and datagram channel.
            quicChannel.close().sync();
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
