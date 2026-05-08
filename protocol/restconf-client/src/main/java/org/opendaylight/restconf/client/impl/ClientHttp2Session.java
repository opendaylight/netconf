/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
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
 * <p>Serves as gateway to Netty {@link Channel}, performs sending requests to server, returns server responses
 * associated. Uses Netty's Http2MultiplexHandler to create a child channel per request.
 */
public final class ClientHttp2Session extends ClientSession {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2Session.class);

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
                callback.onFailure(future.cause());
                return;
            }
            final Http2StreamChannel streamChannel = (Http2StreamChannel) future.getNow();
            request.headers().set(ExtensionHeaderNames.SCHEME.text(), scheme.name());
            streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                private boolean completed = false;
                @Override
                protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
                    if (!completed) {
                        completed = true;
                        callback.onSuccess(response);
                    }
                }
                @Override
                public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
                    if (!completed) {
                        completed = true;
                        callback.onFailure(cause);
                    }
                    ctx.close();
                }
                @Override
                public void channelInactive(final ChannelHandlerContext ctx) {
                    if (!completed) {
                        completed = true;
                        callback.onFailure(new ClosedChannelException());
                    }
                    ctx.fireChannelInactive();
                }
            });

            streamChannel.writeAndFlush(request).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    streamChannel.pipeline().fireExceptionCaught(writeFuture.cause());
                }
            });
        });
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {


    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        clearChannel();
        ctx.fireChannelInactive();
    }
}
