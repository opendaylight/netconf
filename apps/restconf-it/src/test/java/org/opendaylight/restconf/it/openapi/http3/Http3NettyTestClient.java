/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http3;

import static java.net.http.HttpRequest.BodyPublisher;

import com.google.common.collect.ArrayListMultimap;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple HTTP/3 client built on Netty.
 *
 * <p>This client establishes a single QUIC connection to a remote server and
 * allows sending HTTP/3 requests over new request streams created on that
 * connection. Each invocation of {@link #send(HttpRequest)} opens a new
 * HTTP/3 request stream, sends the request headers and optional body,
 * and waits for the response.</p>
 *
 * <p>The implementation is designed for testing purposes and does not aim to
 * provide a full-featured HTTP/3 client</p>.
 */
public final class Http3NettyTestClient implements AutoCloseable {
    private static final long DEFAULT_TIMEOUT = 2;

    private final Channel channel;
    private final QuicChannel quicChannel;
    private final MultiThreadIoEventLoopGroup group;
    private final String username;
    private final String password;

    /**
     * Creates a new HTTP/3 test client and establishes a QUIC connection
     * to the server.
     *
     * @param host the remote server hostname
     * @param port the remote server port
     * @param username optional username for HTTP Basic authentication
     * @param password optional password for HTTP Basic authentication
     * @throws Exception if the QUIC connection or Netty bootstrap fails
     */
    public Http3NettyTestClient(final String host, final int port, final String username, final String password)
            throws Exception {
        this.username = username;
        this.password = password;

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

    /**
     * Creates a new HTTP/3 test client without authentication.
     *
     * @param host the remote server hostname
     * @param port the remote server port
     * @throws Exception if the QUIC connection or Netty bootstrap fails
     */
    public Http3NettyTestClient(final String host, final int port) throws Exception {
        this(host, port, null, null);
    }

    /**
     * Sends an HTTP/3 request and returns the received response.
     *
     * @param request the request to be sent
     * @param discardBody defines whether the response body should be ignored
     * @return the received HTTP/3 response
     * @throws Exception if sending the request or receiving the response fails
     */
    public Http3Response send(final HttpRequest request, final boolean discardBody) throws Exception {
        final var uri =  request.uri();
        final var scheme = uri.getScheme();
        final var host = uri.getHost() + ":" + uri.getPort();
        final var query = uri.getRawQuery();
        final var path = uri.getRawPath() + (query != null && !query.isEmpty() ? "?" + query : "");
        final var timeout = request.timeout().map(Duration::getSeconds).orElse(DEFAULT_TIMEOUT);
        final var body = new StringBuilder();
        final var headers = ArrayListMultimap.<String, String>create();
        final var status = new AtomicInteger();
        final var responseFuture = new CompletableFuture<Http3Response>();

        final var streamChannelFuture = Http3.newRequestStream(quicChannel,
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
                    responseFuture.complete(new Http3Response(
                        HttpResponseStatus.valueOf(status.get()), headers, body.toString()));
                    ctx.close();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    responseFuture.completeExceptionally(cause);
                    ctx.close();
                }

            });

        if (!streamChannelFuture.await(2, TimeUnit.SECONDS)) {
            throw new TimeoutException("Stream creation timed out after 2 seconds");
        }
        final var streamChannel = streamChannelFuture.getNow();

        final var headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers()
            .method(request.method())
            .path(path)
            .authority(host)
            .scheme(scheme);

        // Add basic auth if provided
        if (username != null && password != null) {
            headersFrame.headers().add(HttpHeaderNames.AUTHORIZATION,
                "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));
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

        return responseFuture.get(timeout, TimeUnit.SECONDS);
    }

    public Http3Response send(final HttpRequest request) throws Exception {
        return send(request, false);
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

    /**
     * Simple container for an HTTP/3 response.
     */
    public record Http3Response(HttpResponseStatus status, ArrayListMultimap<String, String> headers, String body) {
        /**
         * {@return the HTTP response status}
         */
        public HttpResponseStatus status() {
            return status;
        }

        /**
         * {@return response headers}
         */
        public ArrayListMultimap<String, String> headers() {
            return headers;
        }

        /**
         * {@return the response body content}
         */
        public String content() {
            return body;
        }
    }
}
