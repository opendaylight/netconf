/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static org.opendaylight.netconf.transport.http.HTTPClient.getAuthFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link EventStreamService} implementation for HTTP/3. Serves as entry point to start (request)
 * SSE stream from server using given connection.
 *
 * <p>Uses {@link Http3#newRequestStream(QuicChannel, ChannelInitializer)} to open a dedicated QUIC stream per SSE
 * request, mirroring {@link ClientHttp2SseService}'s use of a {@code Http2StreamChannelBootstrap}.
 */
@NonNullByDefault
final class ClientHttp3SseService implements EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp3SseService.class);

    private final QuicChannel channel;

    ClientHttp3SseService(final HTTPTransportChannel channel) {
        this.channel = (QuicChannel) channel.channel();
    }

    @Override
    public void startEventStream(final String host, final String requestUri, final EventStreamListener listener,
            final StartCallback callback) {
        if (!channel.isActive()) {
            callback.onStartFailure(new IllegalStateException("Connection is closed"));
            return;
        }

        final var request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, requestUri, EMPTY_BUFFER);
        request.headers()
            .set(HttpHeaderNames.HOST, host)
            .set(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM);

        Http3.newRequestStream(channel, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(final QuicStreamChannel streamChannel) {
                final var pipeline = streamChannel.pipeline();
                pipeline.addLast(new Http3FrameToHttpObjectCodec(false));
                pipeline.addLast(new HttpContentDecompressor(0));
                final var authFactory = getAuthFactory(channel);
                if (authFactory != null) {
                    final var authProvider = authFactory.get();
                    if (authProvider != null) {
                        pipeline.addLast(authProvider);
                    }
                }
                addChannelHandler(streamChannel, requestUri, callback, listener);
            }
        }).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.error("Failed to open HTTP/3 stream for SSE", future.cause());
                request.release();
                callback.onStartFailure(future.cause());
                return;
            }

            final var streamChannel = (QuicStreamChannel) future.getNow();
            streamChannel.writeAndFlush(request).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    streamChannel.pipeline().fireExceptionCaught(writeFuture.cause());
                } else {
                    LOG.debug("SSE request sent to {} on QUIC stream {}", requestUri, streamChannel.streamId());
                }
            });
        });
    }

    private void addChannelHandler(final QuicStreamChannel streamChannel, final String requestUri,
            final StartCallback callback, final EventStreamListener listener) {
        streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<HttpObject>() {
            private boolean startCallbackFired = false;
            private boolean streamStarted = false;
            private @Nullable HttpResponseStatus errorStatus;
            private final StringBuilder errorBody = new StringBuilder();

            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) {
                if (msg instanceof HttpResponse response) {
                    if (!startCallbackFired) {
                        if (response.status().code() == HttpResponseStatus.OK.code()) {
                            LOG.debug("SSE stream successfully established on QUIC stream {}",
                                streamChannel.streamId());
                            startCallbackFired = true;
                            streamStarted = true;
                            listener.onStreamStart();
                            callback.onStreamStarted(ctx::close);
                        } else {
                            errorStatus = response.status();
                            LOG.debug("SSE stream rejected with status {}. Awaiting error body.", errorStatus);
                        }
                    }
                }

                if (msg instanceof HttpContent content) {
                    if (streamStarted) {
                        SseUtils.processChunks(content.content(), listener);
                        if (content instanceof LastHttpContent) {
                            listener.onStreamEnd();
                            streamStarted = false;
                            ctx.close();
                        }
                    } else if (errorStatus != null) {
                        // Buffer the error response
                        errorBody.append(content.content().toString(StandardCharsets.UTF_8));
                        if (content instanceof LastHttpContent) {
                            LOG.warn("SSE stream to {} rejected. Status: {}, Body: {}",
                                requestUri, errorStatus, errorBody);
                            startCallbackFired = true;
                            callback.onStartFailure(
                                new IllegalStateException("Status: " + errorStatus + ", Body: " + errorBody));
                            ctx.close();
                        }
                    }
                }
            }

            @Override
            public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
                if (!startCallbackFired) {
                    startCallbackFired = true;
                    callback.onStartFailure(cause);
                } else {
                    LOG.error("SSE stream error on {}", requestUri, cause);
                }
                ctx.close();
            }

            @Override
            public void channelInactive(final ChannelHandlerContext ctx) {
                if (!startCallbackFired) {
                    startCallbackFired = true;
                    callback.onStartFailure(new ClosedChannelException());
                } else if (streamStarted) {
                    listener.onStreamEnd();
                    streamStarted = false;
                }
                ctx.fireChannelInactive();
            }
        });
    }
}
