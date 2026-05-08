/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.HTTPClient.getAuthFactory;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import java.nio.channels.ClosedChannelException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.restconf.client.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link ClientSession} implementation for HTTP 2.
 *
 * <p>Serves as a gateway to the underlying Netty {@link Channel}. It utilizes Netty's HTTP/2 multiplexing
 * capabilities via a {@link Http2StreamChannelBootstrap} to dynamically spawn a lightweight, isolated
 * child channel for every outbound RESTCONF request, mapping the asynchronous responses back to the caller.
 */
public final class ClientHttp2Session extends ClientSession {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2Session.class);
    private static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    private final HTTPScheme scheme;
    private Http2StreamChannelBootstrap bootstrap;

    public ClientHttp2Session(final HTTPScheme scheme) {
        this.scheme = requireNonNull(scheme);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        super.handlerAdded(ctx);
        this.bootstrap = new Http2StreamChannelBootstrap(ctx.channel());
    }

    @Override
    protected void executeRequest(final @NonNull Channel channel, final @NonNull FullHttpRequest request,
            final @NonNull FutureCallback<FullHttpResponse> callback) {
        bootstrap.open().addListener(future -> {
            if (!future.isSuccess()) {
                LOG.error("Failed to open HTTP/2 stream for request {}", request.uri(), future.cause());
                request.release(); // Prevent memory leak if stream creation fails
                callback.onFailure(future.cause());
                return;
            }

            final var streamChannel = (Http2StreamChannel) future.getNow();
            request.headers().set(ExtensionHeaderNames.SCHEME.text(), scheme.name());
            streamChannel.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(false));
            // Generate and attach Auth handler if available
            final var authFactory = getAuthFactory(channel);
            if (authFactory != null) {
                final var authProvider = authFactory.get();
                if (authProvider != null) {
                    streamChannel.pipeline().addLast(authProvider);
                }
            }
            streamChannel.pipeline().addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
            streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                private boolean completed = false;

                @Override
                protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
                    if (!completed) {
                        completed = true;
                        LOG.debug("Received HTTP/2 response {} on stream {}", response.status(), streamChannel.id());
                        callback.onSuccess(response);
                    }
                }

                @Override
                public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
                    if (!completed) {
                        completed = true;
                        LOG.warn("Exception caught during HTTP/2 request on stream {}", streamChannel.id(), cause);
                        callback.onFailure(cause);
                    }
                    ctx.close();
                }

                @Override
                public void channelInactive(final ChannelHandlerContext ctx) {
                    if (!completed) {
                        completed = true;
                        LOG.debug("HTTP/2 stream {} closed by remote peer", streamChannel.id());
                        callback.onFailure(new ClosedChannelException());
                    }
                    ctx.fireChannelInactive();
                }
            });

            streamChannel.writeAndFlush(request).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    LOG.error("Failed to write request to HTTP/2 stream {}", streamChannel.id(), writeFuture.cause());
                    streamChannel.pipeline().fireExceptionCaught(writeFuture.cause());
                }
            });
        });
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        // No-op. HTTP/2 child channels handle their own reading.
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        clearChannel();
        ctx.fireChannelInactive();
    }
}
