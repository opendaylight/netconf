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
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2StreamChannel;
import java.nio.channels.ClosedChannelException;
import org.eclipse.jdt.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A pipeline handler completing a single {@link FutureCallback} exactly once, with the single expected response,
 * an error, or the channel closing. Used by {@link ClientHttp2Session}, whose per-request child channels each carry
 * exactly one such handler for the lifetime of that channel.
 */
final class SingleResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(SingleResponseHandler.class);

    private final FutureCallback<FullHttpResponse> callback;
    // completed flag is used to guarantee that callback is finished only once
    private boolean completed;

    SingleResponseHandler(final @NonNull FutureCallback<FullHttpResponse> callback) {
        this.callback = requireNonNull(callback);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        if (!completed) {
            completed = true;
            LOG.debug("Received response {} on stream {}", response.status(), streamId(ctx.channel()));
            callback.onSuccess(response);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (!completed) {
            completed = true;
            LOG.warn("Exception caught on stream {}", streamId(ctx.channel()), cause);
            callback.onFailure(cause);
        }
        ctx.close();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (!completed) {
            completed = true;
            LOG.debug("Stream {} closed by remote peer", streamId(ctx.channel()));
            callback.onFailure(new ClosedChannelException());
        }
        ctx.fireChannelInactive();
    }

    private static Object streamId(final Channel channel) {
        return channel instanceof Http2StreamChannel http2 ? http2.stream().id() : channel;
    }
}
