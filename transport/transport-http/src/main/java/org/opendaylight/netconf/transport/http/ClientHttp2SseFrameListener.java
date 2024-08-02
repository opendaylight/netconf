/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.SseConsumerState.AWAITING_ERROR_MESSAGE;
import static org.opendaylight.netconf.transport.http.SseConsumerState.AWAITING_EVENTS;
import static org.opendaylight.netconf.transport.http.SseConsumerState.AWAITING_RESPONSE;
import static org.opendaylight.netconf.transport.http.SseUtils.processChunks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.EventStreamService.StartCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side Server-Sent Event (SSE) HTTP/2 frame listener implementation.
 *
 * <p>
 * Acts as alternative inbound stream processor invoked by {@link Http2ToHttpAdapter}. Intercepts header and data
 * frames for associated stream-id.
 *
 * <p>
 * The expected header frame with response status {@code OK} and {@code content-type=text/event-stream} header
 * indicates the stream request being accepted by the server (stream beginning), then subsequent data frames are
 * treated as event messages. Other headers are treated as server request decline, following data frame is treated
 * as error message.
 *
 * @apiNote
 *     This class is split out on purpose to separate the lifecycle concern handled by
 *     {@link ClientHttp2SseService.FrameListener}.
 */
abstract sealed class ClientHttp2SseFrameListener extends Http2FrameAdapter
        permits ClientHttp2SseService.FrameListener {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2SseFrameListener.class);

    private final @NonNull String uri;
    private final @NonNull StartCallback startCallback;
    private final @NonNull EventStreamListener listener;

    private @NonNull SseConsumerState state = AWAITING_RESPONSE;

    @NonNullByDefault
    ClientHttp2SseFrameListener(final String uri, final EventStreamListener listener,
            final StartCallback startCallback) {
        this.uri = requireNonNull(uri);
        this.listener = requireNonNull(listener);
        this.startCallback = requireNonNull(startCallback);
    }

    @Override
    public final void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int padding, final boolean endStream) throws Http2Exception {
        onHeadersRead(ctx, headers, endStream);
    }

    @Override
    public final void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int streamDependency, final short weight, final boolean exclusive, final int padding,
            final boolean endStream) throws Http2Exception {
        onHeadersRead(ctx, headers, endStream);
    }

    private void onHeadersRead(final ChannelHandlerContext ctx, final Http2Headers headers, final boolean endStream)
            throws Http2Exception {
        if (state != AWAITING_RESPONSE) {
            LOG.error("Unexpected headers frame when data frame is expected. {}", headers);
            ctx.channel().close();
            return;
        }
        final var status = HttpConversionUtil.parseStatus(headers.status());
        if (HttpResponseStatus.OK.equals(status)
            && headers.contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM, true)) {
            // stream request accepted
            state = AWAITING_EVENTS;
            listener.onStreamStart();
            // notify SSE request succeeded with Registration (Closable)
            // TODO: notify server to close stream (require server side frame listener)
            startCallback.onStreamStarted(ClientHttp2SseFrameListener.this::onClose);
            return;
        }

        // stream request declined
        if (endStream) {
            // no error message, use status
            startCallback.onStartFailure(new IllegalStateException("Error: " + status));
            onClose();
        } else {
            // error message to be delivered with data frame
            state = AWAITING_ERROR_MESSAGE;
        }
    }

    @Override
    public final int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data,
            final int padding, final boolean endOfStream) throws Http2Exception {
        final var readableBytes = data.readableBytes();
        switch (state) {
            case AWAITING_ERROR_MESSAGE -> {
                // decline response body
                final var errorMessage = new String(ByteBufUtil.getBytes(data.slice()), StandardCharsets.UTF_8);
                startCallback.onStartFailure(new IllegalStateException(errorMessage));
                onClose();
            }
            case AWAITING_EVENTS -> {
                // data chunk
                processChunks(data, listener);
                if (endOfStream) {
                    // end stream
                    LOG.debug("SSE stream ended for URI={}", uri);
                    listener.onStreamEnd();
                    onClose();
                }
            }
            default -> {
                LOG.warn("Unexpected data frame ({} bytes) when response headers are expected.", readableBytes);
                ctx.channel().close();
            }
        }
        return padding + readableBytes;
    }

    final void onChannelClosed() {
        if (state == AWAITING_EVENTS) {
            // stream already started, close it
            listener.onStreamEnd();
        } else {
            // stream isn't started yet, notify request failed
            startCallback.onStartFailure(new IllegalStateException("Connection closed."));
        }
        onClose();
    }

    abstract void onClose();
}
