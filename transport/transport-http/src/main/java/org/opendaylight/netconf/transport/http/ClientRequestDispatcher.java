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

class ClientRequestDispatcher extends SimpleChannelInboundHandler<FullHttpResponse> implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(ClientRequestDispatcher.class);

    private final Queue<SettableFuture<FullHttpResponse>> queue = new ConcurrentLinkedQueue<>();
    private Channel channel = null;

    ClientRequestDispatcher() {
        super(true); // auto-release
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        channel.closeFuture().addListener(ignored -> onClose());
        super.handlerAdded(ctx);
    }

    @Override
    public ListenableFuture<FullHttpResponse> dispatch(final FullHttpRequest request) {
        final var future = SettableFuture.<FullHttpResponse>create();
        if (channel == null) {
            throw new IllegalStateException("Channel handler context is not initialized yet");
        }
        channel.writeAndFlush(request).addListener(sendFuture -> {
            if (sendFuture.isSuccess()) {
                queue.add(future);
            } else if (sendFuture.isCancelled()) {
                future.cancel(true);
            } else {
                future.setException(sendFuture.cause());
            }
        });
        return future;
    }

    private void onClose() {
        while (!queue.isEmpty()) {
            final var future = queue.poll();
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) throws Exception {
        final var future = queue.poll();
        if (future != null) {
            if (!future.isDone()) {
                // due to content buffer of response message is released (became inaccessible)
                // immediately on consumption (on exit from current method) we need to return
                // a message clone to preserve content
                future.set(response.copy());
            } else {
                LOG.warn("Future is already in Done state -- Dropping response object {}", response);
            }
        } else {
            LOG.warn("Unexpected response while no future associated -- Dropping response object {}", response);
        }
    }
}
