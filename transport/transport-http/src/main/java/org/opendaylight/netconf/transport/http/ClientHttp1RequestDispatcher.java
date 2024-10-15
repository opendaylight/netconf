/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link RequestDispatcher} implementation for HTTP 1.1.
 *
 * <p>Serves as gateway to Netty {@link Channel}, performs sending requests to server, returns server responses
 * associated. Uses request to response mapping via queue -- first accepted response is associated with first request
 * sent.
 */
final class ClientHttp1RequestDispatcher extends ClientRequestDispatcher {
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

    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp1RequestDispatcher.class);

    // TODO: we access the queue only from Netty callbacks: can we use a plain ArrayDeque?
    private final Queue<Req> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void dispatch(final Channel channel, final FullHttpRequest request,
            final FutureCallback<FullHttpResponse> callback) {
        // Queue has to be populated first, simply because a response may arrive sooner than the successful callback
        final var req = new Req(callback);
        queue.add(req);
        channel.writeAndFlush(request).addListener(sent -> {
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
            req.callback.onSuccess(response);
        } else {
            LOG.warn("Unexpected response while no future associated -- Dropping response object {}", response);
        }
    }
}
