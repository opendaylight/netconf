/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNullByDefault;
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
    // maximum length of a single SSE line, in bytes. The server sends a whole notification as one line unless
    // fragmentation is configured, so this needs to be generous.
    @VisibleForTesting
    static final int MAX_LINE_LENGTH = 10 * 1024 * 1024;

    private final String requestUri;
    private final EventStreamService.StartCallback callback;
    private final EventStreamListener listener;

    // FIXME: these are inter-related and undergo a lifecycle: encapsulate into state objects
    // flag used to ensure that onStreamStarted/onStartFailure is called only once
    private boolean startCallbackFired;
    // flag to check if we already have a running stream to decide how to deal with incoming data
    private boolean streamStarted;
    private @Nullable HttpResponseStatus errorStatus;
    private @Nullable StringBuilder errorBody;
    // bytes of the last, not yet terminated line, carried over to the next HttpContent. Content is delivered as it
    // arrives, so an SSE line may be split across several of them. Allocated only while such a line exists.
    private @Nullable ByteBuf incompleteLine;

    @NonNullByDefault
    SseStreamHandler(final String requestUri, final EventStreamService.StartCallback callback,
            final EventStreamListener listener) {
        this.requestUri = requireNonNull(requestUri);
        this.callback = requireNonNull(callback);
        this.listener = requireNonNull(listener);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) {
        switch (msg) {
            // e.g. an error response without a body, which is both the response and its content
            case FullHttpResponse response -> {
                channelRead0(ctx, (HttpResponse) response);
                channelRead0(ctx, (HttpContent) response);
            }
            case HttpResponse response -> channelRead0(ctx, response);
            case HttpContent content -> channelRead0(ctx, content);
            default -> {
                // FIXME: enumerate other possibilities and reject-by-default
                // no-op
            }
        }
    }

    private void channelRead0(final ChannelHandlerContext ctx, final HttpResponse response) {
        if (startCallbackFired) {
            return;
        }

        final var status = response.status();
        if (status.code() != HttpResponseStatus.OK.code()) {
            LOG.debug("SSE stream rejected with status {}. Awaiting error body.", status);
            errorStatus = status;
            return;
        }

        LOG.debug("SSE stream successfully established on channel {}", ctx.channel());
        startCallbackFired = true;
        streamStarted = true;
        listener.onStreamStart();
        callback.onStreamStarted(ctx::close);
    }

    private void channelRead0(final ChannelHandlerContext ctx, final HttpContent content) {
        if (streamStarted) {
            processContent(content.content(), content instanceof LastHttpContent);
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
                callback.onStartFailure(new IllegalStateException("Status: %s, Body: %s".formatted(errorStatus, body)));
                ctx.close();
            }
        }
    }

    private void processContent(final ByteBuf content, final boolean last) {
        final var pending = incompleteLine;
        if (pending != null) {
            // complete the buffered line with the bytes up to and including the first line terminator
            final var lineEnd = last ? content.writerIndex() : firstLineEnd(content);
            if (lineEnd == -1) {
                pending.writeBytes(content);
                checkLineLength(pending.readableBytes());
                return;
            }
            pending.writeBytes(content, lineEnd - content.readerIndex());
            incompleteLine = null;
            try {
                SseUtils.processChunks(pending, listener);
            } finally {
                pending.release();
            }
        }

        // parse the complete lines straight from the content and buffer only the incomplete one after them
        final var readerIndex = content.readerIndex();
        final var linesEnd = last ? content.writerIndex() : lastLineEnd(content);
        if (linesEnd > readerIndex) {
            final var lines = content.readRetainedSlice(linesEnd - readerIndex);
            try {
                SseUtils.processChunks(lines, listener);
            } finally {
                lines.release();
            }
        }
        final var remaining = content.readableBytes();
        if (remaining > 0) {
            checkLineLength(remaining);
            // not readBytes(), its copy inherits the content's maxCapacity and so cannot grow with the rest of the line
            incompleteLine = content.alloc().buffer(remaining).writeBytes(content);
        }
    }

    // index just after the first line terminator in the readable bytes, -1 if there is none
    private static int firstLineEnd(final ByteBuf content) {
        final var from = content.readerIndex();
        final var to = content.writerIndex();
        final var lf = content.indexOf(from, to, (byte) '\n');
        final var cr = content.indexOf(from, to, (byte) '\r');
        final var end = lf == -1 ? cr : cr == -1 ? lf : Math.min(lf, cr);
        return end == -1 ? -1 : end + 1;
    }

    // index just after the last line terminator in the readable bytes, the reader index if there is none
    private static int lastLineEnd(final ByteBuf content) {
        final var from = content.readerIndex();
        final var to = content.writerIndex();
        // searching from writerIndex down to readerIndex finds the last line terminator
        final var end = Math.max(content.indexOf(to, from, (byte) '\n'), content.indexOf(to, from, (byte) '\r'));
        return end == -1 ? from : end + 1;
    }

    private static void checkLineLength(final int length) {
        if (length > MAX_LINE_LENGTH) {
            throw new TooLongFrameException("SSE line exceeds %s bytes".formatted(MAX_LINE_LENGTH));
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        final var pending = incompleteLine;
        if (pending != null) {
            incompleteLine = null;
            pending.release();
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
