/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import java.nio.channels.ClosedChannelException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link EventStreamService} implementation for HTTP/2. Serves as entry point to start (request)
 * SSE stream from server using given connection.
 *
 * <p>Uses Netty's Http2MultiplexHandler to open a dedicated child channel per SSE request.
 */
@NonNullByDefault
final class ClientHttp2SseService implements EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2SseService.class);

    private final Channel channel;
    private final HTTPScheme scheme;
    private final Http2StreamChannelBootstrap bootstrap;

    ClientHttp2SseService(final HTTPTransportChannel channel) {
        this.channel = channel.channel();
        this.scheme = channel.scheme();
        this.bootstrap = new Http2StreamChannelBootstrap(this.channel);
    }

    @Override
    public void startEventStream(final String host, final String requestUri, final EventStreamListener listener,
        final StartCallback callback) {
        if (!channel.isActive()) {
            callback.onStartFailure(new IllegalStateException("Connection is closed"));
            return;
        }

        bootstrap.open().addListener(future -> {
            if (!future.isSuccess()) {
                callback.onStartFailure(future.cause());
                return;
            }
            final Http2StreamChannel streamChannel = (Http2StreamChannel) future.getNow();
            streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<HttpObject>() {
                private boolean startCallbackFired = false;

                @Override
                protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) {
                    if (msg instanceof HttpResponse response) {
                        if (!startCallbackFired) {
                            startCallbackFired = true;
                            if (response.status().code() == HttpResponseStatus.OK.code()) {
                                callback.onStreamStarted(ctx::close);
                            } else {
                                callback.onStartFailure(new IllegalStateException(String.valueOf(response.status())));
                                ctx.close();
                            }
                        }
                    }
                    if (msg instanceof HttpContent content) {
                        SseUtils.processChunks(content.content(), listener);
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
                    }
                    ctx.fireChannelInactive();
                }
            });

            final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, requestUri, EMPTY_BUFFER);
            request.headers()
                .set(HttpHeaderNames.HOST, host)
                .set(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM)
                .set(ExtensionHeaderNames.SCHEME.text(), scheme.name());

            streamChannel.writeAndFlush(request).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    streamChannel.pipeline().fireExceptionCaught(writeFuture.cause());
                } else {
                    LOG.debug("SSE request sent to {} on child channel {}", requestUri, streamChannel);
                }
            });
        });
    }
}
