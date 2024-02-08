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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ClientRequestDispatcher extends SimpleChannelInboundHandler<HttpResponse> implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(ClientRequestDispatcher.class);

    private final Queue<SettableFuture<HttpResponse>> queue = new ConcurrentLinkedQueue<>();
    private ChannelHandlerContext context = null;

    ClientRequestDispatcher() {
        super(true); // auto-release
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        context = ctx;
        ctx.channel().closeFuture().addListener(ignored -> onClose());
        super.handlerAdded(ctx);
    }

    @Override
    public ListenableFuture<HttpResponse> dispatch(final HttpRequest request) {
        final var future = SettableFuture.<HttpResponse>create();
        if (context == null) {
            throw new IllegalStateException("Channel handler context is not initialized yet");
        }
        context.writeAndFlush(request).addListener(sendFuture -> {
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
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpResponse response) throws Exception {
        final var future = queue.poll();
        if (future != null) {
            if (!future.isDone()) {
                future.set(response);
            } else {
                LOG.warn("Future is already in Done state -- Dropping response object {}", response);
            }
        } else {
            LOG.warn("Unexpected response while no future associated -- Dropping response object {}", response);
        }
    }
}
