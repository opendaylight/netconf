/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.SseUtils.SSE_HANDLER_NAME;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import java.util.concurrent.atomic.AtomicReference;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientSseService implements EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(ClientSseService.class);

    private final Channel channel;
    private final AtomicReference<ClientSseHandler> currentHandler = new AtomicReference<>(null);

    ClientSseService(final Channel channel) {
        this.channel = requireNonNull(channel);
        LOG.info("Client SSE enabled on channel {}", channel);
    }

    @Override
    public void startEventStream(final String requestUri, final EventStreamListener listener,
            final FutureCallback<Registration> callback) {
        if (!channel.isActive()) {
            callback.onFailure(new IllegalStateException("Connection is closed"));
            return;
        }
        final var current = currentHandler.get();
        if (current != null) {
            LOG.warn("New event stream requested for URI={} while existing stream is running for URI={}. ",
                requestUri, current.uri());
            callback.onFailure(new IllegalStateException("Another stream is in progress."));
            return;
        }

        ChannelHandlerContext ctx;
        if ((ctx = channel.pipeline().context(HttpObjectAggregator.class)) != null) {
            final var sseHandler = new ClientSseHandler(requestUri, listener, callback, () -> currentHandler.set(null));
            channel.pipeline().addBefore(ctx.name(), SSE_HANDLER_NAME, sseHandler);
            currentHandler.set(sseHandler);

        } else if ((ctx = channel.pipeline().context(HttpToHttp2ConnectionHandler.class)) != null) {
            // add aggregation handler before SSE handler to disable message aggregation by Http2toHttpAdapter
            final var aggregationHandler = new HttpObjectAggregator(1024);
            final var sseHandler = new ClientSseHandler(requestUri, listener, callback,
                () -> {
                    channel.pipeline().remove(aggregationHandler);
                    currentHandler.set(null);
                });
            channel.pipeline().addAfter(ctx.name(), "aggregator", aggregationHandler);
            channel.pipeline().addAfter(ctx.name(), SSE_HANDLER_NAME, sseHandler);
            currentHandler.set(sseHandler);

        } else {
            LOG.warn("Cannot find place for SSE handler");
        }
    }
}
