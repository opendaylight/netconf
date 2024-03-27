/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link RequestDispatcher} implementation for HTTP 1.1.
 *
 * <p>
 * Serves as gateway to Netty {@link Channel}, performs sending requests to server, returns server responses associated.
 * Uses request to response mapping via queue -- first accepted response is associated with first request sent.
 */
class ClientHttp1RequestDispatcher extends SimpleChannelInboundHandler<FullHttpResponse> implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp1RequestDispatcher.class);

    private final Queue<SettableFuture<FullHttpResponse>> queue = new ConcurrentLinkedQueue<>();
    private Channel channel = null;

    ClientHttp1RequestDispatcher() {
        super(true); // auto-release
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        super.handlerAdded(ctx);
    }

    @Override
    public ListenableFuture<FullHttpResponse> dispatch(final FullHttpRequest request) {
        if (channel == null) {
            throw new IllegalStateException("Connection is not established yet");
        }
        final var future = SettableFuture.<FullHttpResponse>create();
        channel.writeAndFlush(request).addListener(sent -> {
            final var cause = sent.cause();
            if (cause == null) {
                queue.add(future);
            } else {
                future.setException(cause);
            }
        });
        return future;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        final var future = queue.poll();
        if (future == null) {
            LOG.warn("Unexpected response while no future associated -- Dropping response object {}", response);
            return;
        }

        if (!future.isDone()) {
            // NB using response' copy to disconnect the content data from channel's buffer allocated.
            // this prevents the content data became inaccessible once byte buffer of original message is released
            // on exit of current method
            future.set(response.copy());
        } else {
            LOG.warn("Future is already in Done state -- Dropping response object {}", response);
        }
    }
}
