/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.SCHEME;
import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.STREAM_ID;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.ssl.SslHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link EventStreamService} implementation for HTTP/2. Serves as entry point to start (request)
 * SSE stream from server using given connection. Mainly acts as {@link Http2FrameListenerProvider} working as
 * {@link Http2ToHttpAdapter} handler extension.
 *
 * <p>
 * On new SSE stream request submits SSE request to server, then builds {@link ClientHttp2SseService} to handle
 * the response frames for associated stream-id. Supports multiple concurrent streams. Only affected stream-id
 * associated traffic is being intercepted.
 */
final class ClientHttp2SseService extends ChannelInboundHandlerAdapter
        implements Http2FrameListenerProvider, EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2SseService.class);

    private final Supplier<Integer> streamIdSupplier;
    private final Map<Integer, ClientHttp2SseFrameListener> frameListeners = new ConcurrentHashMap<>();
    private final Channel channel;
    private final HttpScheme scheme;

    ClientHttp2SseService(final Channel channel, final Supplier<Integer> streamIdSupplier) {
        this.streamIdSupplier = streamIdSupplier;
        this.channel = channel;
        channel.closeFuture().addListener(ignored -> onChannelClosed());
        scheme = channel.pipeline().get(SslHandler.class) != null ? HttpScheme.HTTPS : HttpScheme.HTTP;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        LOG.debug("Client HTTP/2 SSE enabled on channel {}", channel);
    }

    @Override
    public Http2FrameListener getListenerFor(int streamId) {
        return frameListeners.get(streamId);
    }

    @Override
    public void startEventStream(final String requestUri, final EventStreamListener listener,
            final FutureCallback<Registration> callback) {
        if (!channel.isActive()) {
            callback.onFailure(new IllegalStateException("Connection is closed"));
            return;
        }
        final var streamId = streamIdSupplier.get();
        frameListeners.put(streamId, new ClientHttp2SseFrameListener(requestUri, listener, callback,
            () -> frameListeners.remove(streamId)));

        // send SSE stream request through channel so it passes AuthHandler
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, requestUri, EMPTY_BUFFER);
        request.headers()
            .set(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
            .setInt(STREAM_ID.text(), streamId)
            .set(SCHEME.text(), scheme.name());
        channel.writeAndFlush(request);
        LOG.debug("SSE request sent to {}", requestUri);
    }

    void onChannelClosed() {
        frameListeners.forEach((streamId, frameListener) -> frameListener.onChannelClosed());
    }
}
