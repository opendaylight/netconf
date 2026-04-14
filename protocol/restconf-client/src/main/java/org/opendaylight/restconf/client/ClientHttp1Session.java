/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientHttp1Session extends SimpleChannelInboundHandler<FullHttpResponse>
    implements ClientSession {

    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp1Session.class);

    @NonNullByDefault
    private static final class Req {
        final FutureCallback<FullHttpResponse> callback;

        Req(final FutureCallback<FullHttpResponse> callback) {
            this.callback = requireNonNull(callback);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("callback", callback).toString();
        }
    }

    private final Queue<Req> queue = new ConcurrentLinkedQueue<>();
    private Channel channel;

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
    }

    @Override
    public void invoke(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {
        final var local = channel;
        if (local == null) {
            throw new IllegalStateException("Connection is not established yet");
        }

        final var req = new Req(requireNonNull(callback));
        queue.add(req);
        local.writeAndFlush(requireNonNull(request)).addListener(sent -> {
            final var cause = sent.cause();
            if (cause != null && queue.remove(req)) {
                callback.onFailure(cause);
            }
        });
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        final var req = queue.poll();
        if (req != null) {
            // Retain the response because SimpleChannelInboundHandler will release it after this method!
            req.callback.onSuccess(response.retain());
        } else {
            LOG.warn("Unexpected response while no future associated -- Dropping response object {}", response);
        }
    }
}