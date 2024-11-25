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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntSupplier;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link EventStreamService} implementation for HTTP/2. Serves as entry point to start (request)
 * SSE stream from server using given connection. Mainly acts as {@link Http2FrameListenerProvider} working as
 * {@link Http2ToHttpAdapter} handler extension.
 *
 * <p>On new SSE stream request submits SSE request to server, then builds {@link ClientHttp2SseService} to handle
 * the response frames for associated stream-id. Supports multiple concurrent streams. Only affected stream-id
 * associated traffic is being intercepted.
 */
final class ClientHttp2SseService extends ChannelInboundHandlerAdapter
        implements Http2FrameListenerProvider, EventStreamService {
    @NonNullByDefault
    final class FrameListener extends ClientHttp2SseFrameListener {
        private final int streamId;

        private FrameListener(final String uri, final EventStreamListener listener,
                final StartCallback startCallback, final int streamId) {
            super(uri, listener, startCallback);
            this.streamId = streamId;
        }

        @Override
        void onClose() {
            frameListeners.remove(streamId, this);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2SseService.class);

    // TODO: does this need to be concurrent?
    private final ConcurrentMap<Integer, FrameListener> frameListeners = new ConcurrentHashMap<>();
    private final @NonNull IntSupplier streamIdSupplier;
    private final @NonNull Channel channel;
    private final @NonNull HTTPScheme scheme;

    @NonNullByDefault
    ClientHttp2SseService(final HTTPTransportChannel channel, final IntSupplier streamIdSupplier) {
        this.streamIdSupplier = requireNonNull(streamIdSupplier);
        this.channel = channel.channel();
        scheme = channel.scheme();
        this.channel.closeFuture().addListener(ignored -> onChannelClosed());
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        LOG.debug("Client HTTP/2 SSE enabled on channel {}", channel);
    }

    @Override
    public Http2FrameListener getListenerFor(final int streamId) {
        return frameListeners.get(streamId);
    }

    @Override
    public void startEventStream(final String host, final String requestUri, final EventStreamListener listener,
            final StartCallback callback) {
        if (!channel.isActive()) {
            callback.onStartFailure(new IllegalStateException("Connection is closed"));
            return;
        }
        final var streamId = streamIdSupplier.getAsInt();
        frameListeners.put(streamId, new FrameListener(requestUri, listener, callback, streamId));

        // send SSE stream request through channel so it passes AuthHandler
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, requestUri, EMPTY_BUFFER);
        request.headers()
            .set(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .set(HttpHeaderNames.HOST, "example.com")
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
            .setInt(ExtensionHeaderNames.STREAM_ID.text(), streamId)
            .set(ExtensionHeaderNames.SCHEME.text(), scheme);
        channel.writeAndFlush(request);
        LOG.debug("SSE request sent to {}", requestUri);
    }

    private void onChannelClosed() {
        frameListeners.forEach((streamId, frameListener) -> frameListener.onChannelClosed());
    }
}
