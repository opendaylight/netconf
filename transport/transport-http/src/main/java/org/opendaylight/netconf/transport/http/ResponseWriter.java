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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty pipeline integration for outbound responses.
 */
final class ResponseWriter extends ChannelInboundHandlerAdapter {
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

    }

    private synchronized void becameUnwritable(final ChannelHandlerContext ctx) {
        state = Unwritable.INSTANCE;
    }

    /**
     * Start sending the response to a request in chunked encoding,
     *
     * @param status the status to send
     * @param headers the headers to send
     * @return {@code true} if the request was <b>dropped</b> and no further output will be accepted.
     */
    @NonNullByDefault
    synchronized boolean sendResponseStart(final HttpResponseStatus status, final ReadOnlyHttpHeaders headers) {
        // FIXME: version and stream ID as needed
        // FIXME: DefaultHttpResponse, set Transfer-Encoding to chunked

        while (true) {
            switch (state) {
                case Inactive inactive -> {
                    LOG.debug("Rejecting sendResponseStart");
                    return true;
                }
                case Unwritable unwritable -> {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted", e);
                    }
                }
                case Writable writable -> {
                    var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
                    headers.forEach(entry -> response.headers().add(entry.getKey(), entry.getValue()));
                    response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
                    writable.ctx.writeAndFlush(response);
                    return false;
                }
            }
        }
    }

    /**
     * Send a part of a response started via {@link #sendResponseStart(HttpResponseStatus, ReadOnlyHttpHeaders)}.
     *
     * @param chunk response body chunk, formatted according to <a href="">chunked encoding</a>
     * @return {@code true} if the request was <b>dropped</b> and no further output will be accepted.
     */
    @NonNullByDefault
    synchronized boolean sendResponsePart(final ByteBuf chunk) {
        while (true) {
            switch (state) {
                case Inactive inactive -> {
                    LOG.debug("Rejecting reponse part");
                    return true;
                }
                case Unwritable unwrittable -> {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted", e);
                    }
                    continue;
                }
                case Writable writable -> {
                    final var content = new DefaultHttpContent(chunk);
                    writable.ctx.write(content);
                    return true;
                }
            }
        }
    }

    synchronized boolean sendResponseEnd() {
        while (true) {
            switch (state) {
                case Inactive inactive -> {
                    LOG.debug("Rejecting reponse end");
                    return true;
                }
                case Unwritable unwrittable -> {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted", e);
                    }
                }
                case Writable writable -> {
                    writable.ctx.write(LastHttpContent.EMPTY_LAST_CONTENT);
                    return true;
                }
            }
        }
    }
}
