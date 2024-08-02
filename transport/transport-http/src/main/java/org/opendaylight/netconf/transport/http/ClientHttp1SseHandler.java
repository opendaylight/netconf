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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.http.EventStreamService.StartCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side Server-Sent Event (SSE) handler for HTTP 1.1.
 *
 * <p>
 * Deployed by {@link ClientHttp1SseService} after SSE stream request sent to process the server response.
 * The expected {@link HttpResponse} object (header part) with status {@code OK} and
 * {@code content-type=text/event-stream} header indicates the stream request being accepted by the server
 * (stream beginning), then subsequent {@link HttpContent} objects are treated as event messages.
 * Other responses are treated as server request decline, message content as error message.
 *
 * <p>
 * On request decline or stream end the handler self-removed from a channel pipeline to unblock underlying
 * HTTP 1 request/response traffic. While being active all the inbound traffic is consumed.
 *
 * @apiNote
 *     This class is split out on purpose to separate the lifecycle concern handled by
 *     {@link ClientHttp1SseService.ClientSseHandler}.
 */
abstract sealed class ClientHttp1SseHandler extends ChannelInboundHandlerAdapter
        permits ClientHttp1SseService.ClientSseHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp1SseHandler.class);

    private final @NonNull String uri;
    private final @NonNull EventStreamListener listener;
    private final @NonNull StartCallback startCallback;

    private @NonNull SseConsumerState state = AWAITING_RESPONSE;

    ClientHttp1SseHandler(final @NonNull String uri, final @NonNull EventStreamListener listener,
            final @NonNull StartCallback startCallback) {
        this.uri = requireNonNull(uri);
        this.listener = requireNonNull(listener);
        this.startCallback = requireNonNull(startCallback);
    }

    final String uri() {
        return uri;
    }

    @Override
    public final void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        switch (msg) {
            case HttpResponse response -> onResponse(ctx, response);
            case HttpContent content -> onContent(ctx, content);
            default -> {
                LOG.error("Unexpected SSE message {}", msg);
                ctx.channel().close();
            }
        }
    }

    private void onResponse(final ChannelHandlerContext ctx, final HttpResponse response) {
        if (state != AWAITING_RESPONSE) {
            LOG.error("Unexpected response header when data chunk is expected. {}", response);
            ctx.channel().close();
            return;
        }
        // response header to stream request
        if (HttpResponseStatus.OK.equals(response.status())
            && response.headers().contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM, true)) {
            // stream request accepted
            state = AWAITING_EVENTS;
            listener.onStreamStart();
            // notify SSE request succeeded with Registration (Closable)
            startCallback.onStreamStarted(() -> {
                if (!ctx.isRemoved() && ctx.channel().isActive()) {
                    // terminate connection if current handler is attached to active connection
                    // so the server is notified the stream no longer consumed
                    ctx.channel().close();
                }
            });
            return;
        }

        // stream request declined
        if (response instanceof FullHttpResponse fullResponse) {
            // error message is in current object
            onRequestDeclineError(ctx, fullResponse.content(), true);
        } else {
            // error message to be delivered as separate object
            state = AWAITING_ERROR_MESSAGE;
        }
    }

    private void onContent(final ChannelHandlerContext ctx, final HttpContent content) {
        switch (state) {
            case AWAITING_ERROR_MESSAGE ->
                // decline response body
                onRequestDeclineError(ctx, content.content(), content instanceof LastHttpContent);
            case AWAITING_EVENTS -> {
                // event chunk
                processChunks(content.content(), listener);
                // if last one
                if (content instanceof LastHttpContent) {
                    // end stream
                    LOG.debug("SSE stream ended for URI={}", uri);
                    listener.onStreamEnd();
                    // nothing to expect -- remove self
                    ctx.pipeline().remove(this);
                }
            }
            default -> {
                LOG.error("Unexpected data chunk when response header is expected. {}", content);
                ctx.channel().close();
            }
        }
    }

    private void onRequestDeclineError(final ChannelHandlerContext ctx, final ByteBuf error,
            final boolean endOfStream) {
        final var errorMessage = new String(ByteBufUtil.getBytes(error), StandardCharsets.UTF_8);
        startCallback.onStartFailure(new IllegalStateException(errorMessage));
        if (endOfStream) {
            ctx.pipeline().remove(this);
        }
    }

    @Override
    public final void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        if (state == AWAITING_EVENTS) {
            // stream already started, close it
            listener.onStreamEnd();
        } else {
            // stream isn't started yet, notify request failed
            startCallback.onStartFailure(new IllegalStateException("Connection closed."));
        }
    }

    @Override
    public abstract void handlerRemoved(ChannelHandlerContext ctx);
}
