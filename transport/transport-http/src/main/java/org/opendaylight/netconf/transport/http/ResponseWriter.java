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
import java.util.ArrayDeque;
import java.util.Queue;
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

    private final Queue<HttpObject> pendingChunks = new ArrayDeque<>();

    private @NonNull State state = Inactive.INSTANCE;

    @Override
    public synchronized void handlerAdded(final ChannelHandlerContext ctx) {
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
    synchronized boolean sendResponseStart(final HttpResponseStatus status, final ReadOnlyHttpHeaders headers,
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
                pendingChunks.add(response);
                return true;
            }
            case Writable writable -> {
                writable.ctx.writeAndFlush(response);
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
    synchronized boolean sendResponsePart(final ByteBuf chunk) {
        final var content = new DefaultHttpContent(chunk);
        switch (state) {
            case Inactive ignored -> {
                LOG.debug("Rejecting response part");
                return false;
            }
            case Unwritable ignored -> {
                LOG.debug("Channel unwritable, adding part chunk to queue");
                pendingChunks.add(content);
                return true;
            }
            case Writable writable -> {
                if (pendingChunks.isEmpty()) {
                    writable.ctx.writeAndFlush(content);
                } else {
                    pendingChunks.add(content);
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
                pendingChunks.add(lastContent);
                return true;
            }
            case Writable writable -> {
                if (pendingChunks.isEmpty()) {
                    writable.ctx.writeAndFlush(lastContent);
                } else {
                    pendingChunks.add(lastContent);
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
}
