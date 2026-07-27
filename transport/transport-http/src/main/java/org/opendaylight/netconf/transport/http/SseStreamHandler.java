/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A pipeline handler driving a single SSE stream's start-then-stream-or-fail state machine. Shared by
 * {@link ClientHttp2SseService} and {@link ClientHttp3SseService}, whose per-request child/stream channels each
 * carry exactly one such handler for the lifetime of that channel.
 */
final class SseStreamHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static final Logger LOG = LoggerFactory.getLogger(SseStreamHandler.class);

    private final String requestUri;
    private final EventStreamService.StartCallback callback;
    private final EventStreamListener listener;
    private final StringBuilder errorBody = new StringBuilder();

    // flag used to ensure that onStreamStarted/onStartFailure is called only once
    private boolean startCallbackFired = false;
    // flag to check if we already have a running stream to decide how to deal with incoming data
    private boolean streamStarted = false;
    private @Nullable HttpResponseStatus errorStatus;

    SseStreamHandler(final @NonNull String requestUri, final EventStreamService.@NonNull StartCallback callback,
            final @NonNull EventStreamListener listener) {
        this.requestUri = requireNonNull(requestUri);
        this.callback = requireNonNull(callback);
        this.listener = requireNonNull(listener);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) {
        if (msg instanceof HttpResponse response) {
            if (!startCallbackFired) {
                if (response.status().code() == HttpResponseStatus.OK.code()) {
                    LOG.debug("SSE stream successfully established on channel {}", ctx.channel());
                    startCallbackFired = true;
                    streamStarted = true;
                    listener.onStreamStart();
                    callback.onStreamStarted(ctx::close);
                } else {
                    errorStatus = response.status();
                    LOG.debug("SSE stream rejected with status {}. Awaiting error body.", errorStatus);
                }
            }
        }

        if (msg instanceof HttpContent content) {
            if (streamStarted) {
                SseUtils.processChunks(content.content(), listener);
                if (content instanceof LastHttpContent) {
                    listener.onStreamEnd();
                    streamStarted = false;
                    ctx.close();
                }
            } else if (errorStatus != null) {
                // Buffer the error response
                errorBody.append(content.content().toString(StandardCharsets.UTF_8));
                if (content instanceof LastHttpContent) {
                    LOG.warn("SSE stream to {} rejected. Status: {}, Body: {}", requestUri, errorStatus, errorBody);
                    startCallbackFired = true;
                    callback.onStartFailure(
                        new IllegalStateException("Status: " + errorStatus + ", Body: " + errorBody));
                    ctx.close();
                }
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
