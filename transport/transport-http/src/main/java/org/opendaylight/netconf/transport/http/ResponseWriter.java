/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty pipeline integration for outbound responses.
 */
public final class ResponseWriter extends ChannelInboundHandlerAdapter {
    @NonNullByDefault
    private sealed interface State {
        // Nothing else
    }

    @NonNullByDefault
    private static final class Inactive implements State {
        private static final Inactive INSTANCE = new Inactive();
    }

    @NonNullByDefault
    private static final class Unwritable implements State {
        private static final Unwritable INSTANCE = new Unwritable();
    }

    @NonNullByDefault
    private record Writable(ChannelHandlerContext ctx) implements State {
        Writable {
            requireNonNull(ctx);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ResponseWriter.class);

    private final ArrayBlockingQueue<HttpObject> pendingChunks = new ArrayBlockingQueue<>(1);

    private volatile @NonNull State state = Inactive.INSTANCE;

    private volatile ChannelHandlerContext context;

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.context = requireNonNull(ctx);
        state = ctx.channel().isWritable() ? new Writable(ctx) : new Unwritable();
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx) throws Exception {
        try {
            if (ctx.channel().isWritable()) {
                becameWritable(ctx);
            } else {
                becameUnwritable(ctx);
            }
        } finally {
            super.channelWritabilityChanged(ctx);
        }
    }

    private synchronized void becameWritable(final ChannelHandlerContext ctx) {
        state = new Writable(ctx);
        sendQueue();
    }

    private synchronized void becameUnwritable(final ChannelHandlerContext ctx) {
        state = Unwritable.INSTANCE;
    }

    /**
     * Start sending the response to a request in chunked encoding.
     *
     * @param status  the status to send
     * @param headers the headers to send
     * @param version the HTTP version
     * @return {@code false} if the request was <b>dropped</b> and no further output will be accepted.
     */
    @NonNullByDefault
    boolean sendResponseStart(final HttpResponseStatus status, final ReadOnlyHttpHeaders headers,
            final HttpVersion version) {
        final var response = new DefaultHttpResponse(version, status);
        headers.forEach(entry -> response.headers().add(entry.getKey(), entry.getValue()));
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        switch (state) {
            case Inactive ignored -> {
                LOG.debug("Rejecting sendResponseStart");
                return false;
            }
            case Unwritable ignored -> {
                LOG.debug("Channel unwritable, adding first chunk to queue");
                blockPut(response);
                scheduleDrain();
                return true;
            }
            case Writable writable -> {
                blockPut(response);
                scheduleDrain();
                return true;
            }
        }
    }

    /**
     * Send a part of a response started via {@link #sendResponseStart(HttpResponseStatus, ReadOnlyHttpHeaders,
     * HttpVersion)}.
     *
     * @param chunk response body chunk, formatted according to <a href="">chunked encoding</a>
     * @return {@code false} if the request was <b>dropped</b> and no further output will be accepted.
     */
    @NonNullByDefault
    boolean sendResponsePart(final ByteBuf chunk) {
        final var content = new DefaultHttpContent(chunk);
        switch (state) {
            case Inactive ignored -> {
                LOG.debug("Rejecting response part");
                return false;
            }
            case Unwritable ignored -> {
                LOG.debug("Channel unwritable, adding part chunk to queue");
                blockPut(content);
                scheduleDrain();
                return true;
            }
            case Writable writable -> {
                if (pendingChunks.isEmpty()) {
                    writable.ctx.writeAndFlush(content);
                } else {
                    blockPut(content);
                    scheduleDrain();
                }
                return true;
            }
        }
    }


    /**
     * Send an end of a response.
     *
     * @param trailers trailer headers
     * @return {@code false} if the request was <b>dropped</b> and last chunk was not send.
     */
    synchronized boolean sendResponseEnd(final ReadOnlyHttpHeaders trailers) {
        final var lastContent = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        lastContent.trailingHeaders().add(trailers);
        switch (state) {
            case Inactive ignored -> {
                LOG.debug("Rejecting response end");
                return false;
            }
            case Unwritable ignored -> {
                LOG.debug("Channel unwritable, adding end chunk to queue");
                blockPut(lastContent);
                scheduleDrain();
                return true;
            }
            case Writable writable -> {
                if (pendingChunks.isEmpty()) {
                    writable.ctx.writeAndFlush(lastContent);
                } else {
                    blockPut(lastContent);
                    scheduleDrain();
                }
                return true;
            }
        }
    }

    private synchronized void sendQueue() {
        while (state instanceof Writable(ChannelHandlerContext ctx) && !pendingChunks.isEmpty()) {
            ctx.writeAndFlush(pendingChunks.poll());
        }
    }

    private void blockPut(final HttpObject obj) {
        try {
            pendingChunks.put(obj);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (obj instanceof DefaultHttpContent defaultHttpContent) {
                defaultHttpContent.content().release();
            }
            throw new RejectedExecutionException("Interrupted while enqueuing outbound object", e);
        }
    }

    private static void ensureNotOnEventLoop(final ChannelHandlerContext ctx, final HttpObject toReleaseIfInvalid) {
        if (ctx.executor().inEventLoop()) {
            if (toReleaseIfInvalid instanceof DefaultHttpContent defaultHttpContent) {
                defaultHttpContent.content().release();
            }
            throw new RejectedExecutionException("Blocking send called from the event loop; would block.");
        }
    }

    /** Schedule a drain on the event loop. */
    private void scheduleDrain() {
        final var localCtx = context;
        if (localCtx == null) {
            return;
        }
        localCtx.executor().execute(() -> drainOnEventLoop(localCtx));
    }

    private void drainOnEventLoop(final ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) {
            state = Inactive.INSTANCE;
            return;
        }
        if (!ctx.channel().isWritable()) {
            state = Unwritable.INSTANCE;
            return;
        }
        state = new Writable(ctx);

        while (true) {
            if (!ctx.channel().isWritable()) {
                state = Unwritable.INSTANCE;
                break;
            }
            final HttpObject obj = pendingChunks.poll();
            if (obj == null) {
                break;
            }
            ctx.write(obj);
        }
        // FIXME Does it require boolean guard?
        ctx.flush();
    }
}
