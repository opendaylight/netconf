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
import static org.opendaylight.netconf.transport.http.HTTPClient.getAuthFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.concurrent.FutureListener;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.eclipse.jdt.annotation.NonNull;
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
    private static final class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final @NonNull String requestUri;
        private final @NonNull StartCallback callback;
        private final @NonNull EventStreamListener listener;

        // FIXME: these are inter-related and undergo a lifecycle: encapsulate into state objects
        private boolean startCallbackFired;
        private boolean streamStarted;
        private HttpResponseStatus errorStatus;
        private StringBuilder errorBody;

        ResponseHandler(final String requestUri, final StartCallback callback, final EventStreamListener listener) {
            this.requestUri = requireNonNull(requestUri);
            this.callback = requireNonNull(callback);
            this.listener = requireNonNull(listener);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) {
            switch (msg) {
                case HttpContent content -> channelRead0(ctx, content);
                case HttpResponse response -> channelRead0(ctx, response);
                default -> {
                    // FIXME: enumerate other possibilities and reject-by-default
                    // no-op
                }
            }
        }

        private void channelRead0(final ChannelHandlerContext ctx, final @NonNull HttpResponse response) {
            if (startCallbackFired) {
                return;
            }

            final var status = response.status();
            if (status.code() != HttpResponseStatus.OK.code()) {
                LOG.debug("SSE stream rejected with status {}. Awaiting error body.", status);
                errorStatus = status;
                return;
            }

            final var channel = (Http2StreamChannel) ctx.channel();
            LOG.debug("SSE stream successfully established on child channel {}", channel.id());
            startCallbackFired = true;
            streamStarted = true;
            listener.onStreamStart();
            callback.onStreamStarted(ctx::close);
        }

        private void channelRead0(final ChannelHandlerContext ctx, final @NonNull HttpContent content) {
            if (streamStarted) {
                SseUtils.processChunks(content.content(), listener);
                if (content instanceof LastHttpContent) {
                    listener.onStreamEnd();
                    streamStarted = false;
                    ctx.close();
                }
                return;
            }
            if (errorStatus != null) {
                var sb = errorBody;
                if (sb == null) {
                    errorBody = sb = new StringBuilder();
                }

                // Buffer the error response
                sb.append(content.content().toString(StandardCharsets.UTF_8));
                if (content instanceof LastHttpContent) {
                    final var body = sb.toString();
                    errorBody = null;

                    LOG.warn("SSE stream to {} rejected. Status: {}, Body: {}", requestUri, errorStatus, body);
                    startCallbackFired = true;
                    callback.onStartFailure(new IllegalStateException("Status: " + errorStatus + ", Body: " + body));
                    ctx.close();
                }
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            if (!startCallbackFired) {
                startCallbackFired = true;
                callback.onStartFailure(cause);
            } else {
                LOG.error("SSE stream error on {}", requestUri, cause);
            }
            ctx.close();
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            if (!startCallbackFired) {
                startCallbackFired = true;
                callback.onStartFailure(new ClosedChannelException());
            } else if (streamStarted) {
                listener.onStreamEnd();
                streamStarted = false;
            }
            ctx.fireChannelInactive();
        }
    }

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
            pipeline.addLast(new ResponseHandler(requestUri, callback, listener));

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
