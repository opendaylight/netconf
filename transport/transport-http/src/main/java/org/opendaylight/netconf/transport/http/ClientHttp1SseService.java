/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.SseUtils.SSE_HANDLER_NAME;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link EventStreamService} implementation for HTTP 1.1. Serves as entry point to start (request)
 * SSE stream from server using given connection.
 *
 * <p>
 * On start stream request submits the SSE stream request to server then builds and deploys the
 * {@link ClientHttp1SseHandler} instance to handle the server response. SSE Handler is deployed before
 * {@link HttpObjectAggregator} so it can intercept response header and body chunks immediately on arrival.
 */
final class ClientHttp1SseService implements EventStreamService {
    private final class ClientSseHandler extends ClientHttp1SseHandler {
        @NonNullByDefault
        ClientSseHandler(final String uri, final EventStreamListener listener,
                final FutureCallback<Registration> requestCallback) {
            super(uri, listener, requestCallback);
        }

        @Override
        public void handlerRemoved(final ChannelHandlerContext ctx) {
            currentHandler = null;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp1SseService.class);

    private final @NonNull Channel channel;

    // TODO: does this have to be a volatile?
    private volatile ClientSseHandler currentHandler = null;

    ClientHttp1SseService(final @NonNull Channel channel) {
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
        final var existing = currentHandler;
        if (existing != null) {
            LOG.warn("New event stream requested for URI={} while existing stream is running for URI={}. ",
                requestUri, existing.uri());
            callback.onFailure(new IllegalStateException("Another stream is in progress."));
            return;
        }

        // init SSE handler
        final var pipeline = channel.pipeline();
        final var aggregatorContext = pipeline.context(HttpObjectAggregator.class);
        if (aggregatorContext == null) {
            LOG.error("Cannot init SSE Handler because pipeline has no HttpObjectAggregator expected.");
            callback.onFailure(new IllegalStateException("SSE Handler position cannot be defined"));
            channel.close();
            return;
        }

        final var sseHandler = new ClientSseHandler(requestUri, listener, callback);
        pipeline.addBefore(aggregatorContext.name(), SSE_HANDLER_NAME, sseHandler);
        currentHandler = sseHandler;

        // send SSE stream request
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, requestUri, EMPTY_BUFFER);
        request.headers()
            .set(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        channel.writeAndFlush(request);
        LOG.debug("SSE request sent to {}", requestUri);
    }
}
