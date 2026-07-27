/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static org.opendaylight.netconf.transport.http.HTTPClient.getAuthFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.concurrent.FutureListener;
import java.util.Locale;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link EventStreamService} implementation for HTTP/2. Serves as entry point to start (request)
 * SSE stream from server using given connection.
 *
 * <p>Uses a {@link Http2StreamChannelBootstrap} to open a dedicated child channel per SSE request.
 */
final class ClientHttp2SseService implements EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2SseService.class);

    @NonNullByDefault
    private final Channel channel;
    @NonNullByDefault
    private final HTTPScheme scheme;
    @NonNullByDefault
    private final Http2StreamChannelBootstrap bootstrap;

    @NonNullByDefault
    ClientHttp2SseService(final HTTPTransportChannel channel) {
        this.channel = channel.channel();
        scheme = channel.scheme();
        bootstrap = new Http2StreamChannelBootstrap(this.channel);
    }

    @Override
    public void startEventStream(final String host, final String requestUri, final EventStreamListener listener,
            final StartCallback callback) {
        if (!channel.isActive()) {
            callback.onStartFailure(new IllegalStateException("Connection is closed"));
            return;
        }

        bootstrap.open().addListener((FutureListener<Http2StreamChannel>) sf -> {
            final var sfCause = sf.cause();
            if (sfCause != null) {
                LOG.error("Failed to open HTTP/2 child stream for SSE", sfCause);
                callback.onStartFailure(sfCause);
                return;
            }

            final var ch = sf.getNow();
            final var pipeline = ch.pipeline()
                .addLast(new Http2StreamFrameToHttpObjectCodec(false))
                .addLast(new HttpContentDecompressor(0));
            final var authFactory = getAuthFactory(channel);
            if (authFactory != null) {
                final var authProvider = authFactory.get();
                if (authProvider != null) {
                    pipeline.addLast(authProvider);
                }
            }
            pipeline.addLast(new SseStreamHandler(requestUri, callback, listener));

            final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, requestUri,
                EMPTY_BUFFER);
            request.headers()
                .set(HttpHeaderNames.HOST, host)
                .set(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM)
                .set(ExtensionHeaderNames.SCHEME.text(), scheme.name().toLowerCase(Locale.ENGLISH));

            ch.writeAndFlush(request).addListener((ChannelFutureListener) wf -> {
                final var wfCause = wf.cause();
                if (wfCause != null) {
                    ch.pipeline().fireExceptionCaught(wfCause);
                } else {
                    LOG.debug("SSE request sent to {} on child channel {}", requestUri, ch);
                }
            });
        });
    }
}
