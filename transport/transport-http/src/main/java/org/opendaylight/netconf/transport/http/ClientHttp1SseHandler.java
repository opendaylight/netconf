/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.SseUtils.processChunks;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side Server-Sent Event (SSE) handler for HTTP 1.1.
 *
 * <p>
 * Deployed by {@link ClientHttp1SseService} after SSE stream request to process the server response.
 * The expected {@link HttpResponse} object (header part) with status {@code OK} and
 * {@code content-type=text/event-stream} header indicates the stream request being accepted by the server
 * (stream beginning), then subsequent {@link HttpContent} objects are treated as event messages.
 * Other responses are treated as server request decline, message content as error message.
 *
 * <p>
 * On request decline or stream end the handler self-removed from a channel pipeline to unblock underlying
 * HTTP 1 request/response traffic. While being active all the inbound traffic is consumed.
 *
 */
final class ClientHttp1SseHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp1SseHandler.class);

    private final String uri;
    private final FutureCallback<Registration> requestCallback;
    private final EventStreamListener listener;
    private final Runnable onClose;
    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private final AtomicBoolean awaitingErrorMessage = new AtomicBoolean(false);

    ClientHttp1SseHandler(final String uri, final EventStreamListener listener,
            final FutureCallback<Registration> requestCallback, final Runnable onClose) {
        this.uri = requireNonNull(uri);
        this.listener = requireNonNull(listener);
        this.requestCallback = requestCallback;
        this.onClose = onClose;
    }

    String uri() {
        return uri;
    }

    EventStreamListener listener() {
        return listener;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (!streaming.get() && msg instanceof HttpResponse response) {
            // response header to stream request
            if (HttpResponseStatus.OK.equals(response.status())
                && response.headers().contains(HttpHeaderNames.CONTENT_TYPE, SseUtils.TEXT_EVENT_STREAM, true)) {
                // stream request accepted
                streaming.set(true);
                listener.onStreamStart();
                // notify SSE request succeeded with Registration (Closable)
                requestCallback.onSuccess(() -> {
                    if (!ctx.isRemoved() && ctx.channel().isActive()) {
                        // terminate connection if current handler is attached to active connection
                        // so the server is notified the stream no longer consumed
                        ctx.channel().close();
                    }
                });
            } else {
                // stream request declined
                if (msg instanceof FullHttpResponse fullResponse) {
                    // error message is in current object
                    onRequestDeclineError(ctx, fullResponse.content(), true);
                } else {
                    // error message to be delivered as separate object
                    awaitingErrorMessage.set(true);
                }
            }

        } else if (awaitingErrorMessage.get() && msg instanceof HttpContent content) {
            // decline response body
            onRequestDeclineError(ctx, content.content(), content instanceof LastHttpContent);

        } else if (streaming.get() && msg instanceof HttpContent chunk) {
            // event chunk
            processChunks(chunk.content(), listener);

            // if last one
            if (msg instanceof LastHttpContent) {
                // end stream
                LOG.debug("SSE stream ended for URI={}", uri);
                listener.onStreamEnd();
                streaming.set(false);
                // nothing to expect -- remove self
                ctx.pipeline().remove(this);
            }
        } else {
            LOG.warn("Unexpected SSE message {}", msg);
        }
    }

    private void onRequestDeclineError(final ChannelHandlerContext ctx, final ByteBuf error,
            final boolean endOfStream) {
        final var errorMessage = new String(ByteBufUtil.getBytes(error), StandardCharsets.UTF_8);
        requestCallback.onFailure(new IllegalStateException(errorMessage));
        if (endOfStream) {
            ctx.pipeline().remove(this);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {
        if (onClose != null) {
            onClose.run();
        }
    }
}
