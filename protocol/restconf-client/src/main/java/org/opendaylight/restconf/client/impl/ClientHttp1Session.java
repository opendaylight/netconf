/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import java.util.ArrayDeque;
import java.util.Queue;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.client.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link ClientSession} implementation for HTTP 1.1.
 *
 * <p>Serves as gateway to Netty {@link Channel}, performs sending requests to server, returns server responses
 * associated. Uses request to response mapping via queue -- first accepted response is associated with first request
 * sent.
 */
public final class ClientHttp1Session extends ClientSession {
    /**
     * A request context. This wraps the callback to invoke so that we can rely on identity-based equality.
     */
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

    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp1Session.class);

    private final Queue<Req> queue = new ArrayDeque<>();
    private Channel channel;

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
    }

    @Override
    public void invoke(final @NonNull FullHttpRequest request,
            final @NonNull FutureCallback<FullHttpResponse> callback) {
        final var local = channel;
        if (local == null) {
            throw new IllegalStateException("Connection is not established yet");
        }
        // Queue has to be populated first, simply because a response may arrive sooner than the successful callback
        final var req = new Req(callback);
        local.eventLoop().execute(() -> {
            queue.add(req);
            local.writeAndFlush(request).addListener(sent -> {
                final var cause = sent.cause();
                if (cause != null && queue.remove(req)) {
                    callback.onFailure(cause);
                }
            });
        });
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        final var req = queue.poll();
        if (req != null) {
            req.callback.onSuccess(response);
        } else {
            LOG.warn("Unexpected response while no future associated -- Dropping response object {}", response);
        }
    }
}
