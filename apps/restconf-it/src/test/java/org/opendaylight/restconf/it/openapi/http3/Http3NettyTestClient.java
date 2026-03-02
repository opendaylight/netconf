/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http3;

import static java.net.http.HttpRequest.BodyPublisher;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
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
import io.netty.util.ReferenceCountUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Http3NettyTestClient implements AutoCloseable {
    private static final long DEFAULT_TIMEOUT = 2;

    private final Channel channel;
    private final QuicChannel quicChannel;
    private final MultiThreadIoEventLoopGroup group;
    private final String authName;
    private final String authPassword;

    public Http3NettyTestClient(final String host, final int port, final String username, final String password)
            throws Exception {
        this.authName = username;
        this.authPassword = password;

        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

        final var context = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(Http3.supportedApplicationProtocols()).build();

        final var codec = Http3.newQuicClientCodecBuilder()
            .sslContext(context)
            .maxIdleTimeout(50000, TimeUnit.MILLISECONDS)
            .initialMaxData(10000000)
            .initialMaxStreamDataBidirectionalLocal(1000000)
            .build();

        channel = new Bootstrap().group(group)
            .channel(NioDatagramChannel.class)
            .handler(codec)
            .bind(0)
            .sync()
            .channel();

        quicChannel = QuicChannel.newBootstrap(channel)
            .handler(new Http3ClientConnectionHandler())
            .remoteAddress(new InetSocketAddress(host, port))
            .connect()
            .get();

    }

    public Http3NettyTestClient(final String host, final int port) throws Exception {
        this(host, port, null, null);
    }

    public Http3Response send(final HttpRequest request) throws Exception {
        return send(request, false);
    }

    public Http3Response send(final HttpRequest request, final boolean discardBody) throws Exception {
        final var uri =  request.uri();
        final var scheme = uri.getScheme();
        final var host = uri.getHost() + ":" + uri.getPort();
        final var query = uri.getRawQuery();
        final var path = uri.getRawPath() + (query != null && !query.isEmpty() ? "?" + query : "");
        final var timeout = request.timeout().map(Duration::getSeconds).orElse(DEFAULT_TIMEOUT);

        final var body = new StringBuilder();
        final var headers = new HashMap<String, String>();
        final var status = new AtomicInteger();
        final var responseFuture = new CompletableFuture<Http3Response>();

        final var streamChannel = Http3.newRequestStream(quicChannel,
            new Http3RequestStreamInboundHandler() {
                @Override
                protected void channelRead(final ChannelHandlerContext ctx, final Http3HeadersFrame frame) {
                    frame.headers().forEach(header -> {
                            final var name = header.getKey().toString();
                            final var value = header.getValue().toString();

                            if (name.equals(":status")) {
                                status.set(Integer.parseInt(value));
                            } else {
                                headers.put(name, value);
                            }
                        }
                    );
                    ReferenceCountUtil.release(frame);
                }

                @Override
                protected void channelRead(final ChannelHandlerContext ctx, final Http3DataFrame frame) {
                    if (!discardBody) {
                        body.append(frame.content().toString(StandardCharsets.UTF_8));
                    }
                    ReferenceCountUtil.release(frame);
                }

                @Override
                protected void channelInputClosed(final ChannelHandlerContext ctx) {
                    responseFuture.complete(new Http3Response(status.get(), headers, body.toString()));
                    ctx.close();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    responseFuture.completeExceptionally(cause);
                    ctx.close();
                }

            }).sync().getNow();

        final var headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers()
            .method(request.method())
            .path(path)
            .authority(host)
            .scheme(scheme);

        // Add basic auth if provided
        if (authName != null && authPassword != null) {
            headersFrame.headers().add(HttpHeaderNames.AUTHORIZATION,
                "Basic " + Base64.getEncoder().encodeToString((authName + ":" + authPassword).getBytes()));
        }

        // Add other request headers
        for (final var header : request.headers().map().entrySet()) {
            final var values = header.getValue();
            for (final var value : values) {
                headersFrame.headers().add(header.getKey(), value);
            }
        }

        // Add body if present
        final var bodyPublisher = request.bodyPublisher().orElse(null);
        if (bodyPublisher != null) {
            final var dataFrame = new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(getBody(bodyPublisher)));
            streamChannel.writeAndFlush(headersFrame);
            streamChannel.writeAndFlush(dataFrame).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();
        } else {
            streamChannel.writeAndFlush(headersFrame).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();
        }
        streamChannel.closeFuture().sync();

        return responseFuture.get(timeout, TimeUnit.SECONDS);
    }

    private static byte[] getBody(final BodyPublisher publisher) throws Exception {
        // Use a ByteArrayOutputStream to collect all bytes
        final var outputStream = new ByteArrayOutputStream();
        final var future = new CompletableFuture<Void>();

        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription sub) {
                sub.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final ByteBuffer item) {
                try {
                    final var bytes = new byte[item.remaining()];
                    item.get(bytes);
                    outputStream.write(bytes);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onError(final Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(null);
            }
        });

        future.get(2, TimeUnit.SECONDS);
        return outputStream.toByteArray();
    }

    @Override
    public void close() throws Exception {
        quicChannel.close().sync();
        channel.close().sync();
        group.shutdownGracefully();
    }

    public static final class Http3Response {
        private final HttpResponseStatus status;
        private final Map<String, String> headers;
        private final String body;

        Http3Response(final int status, final Map<String, String> headers, final String body) {
            this.status = HttpResponseStatus.valueOf(status);
            this.headers = headers;
            this.body = body;
        }

        public HttpResponseStatus status() {
            return status;
        }

        public Map<String, String> headers() {
            return headers;
        }

        public String content() {
            return body;
        }
    }
}
