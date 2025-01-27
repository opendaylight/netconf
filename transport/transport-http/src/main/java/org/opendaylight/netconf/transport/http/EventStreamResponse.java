/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.STREAM_ID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.concurrent.TimeUnit;

public final class EventStreamResponse implements Response {
    private static final ByteBuf PING_MESSAGE =
        Unpooled.wrappedBuffer(new byte[] { ':', 'p', 'i', 'n', 'g', '\r', '\n', '\r', '\n' }).asReadOnly();

    private final ChannelHandler sender;
    private final ChannelHandler handler;
    private final int sseHeartbeatIntervalMillis;
    private final HttpResponseStatus status;

    private ChannelHandlerContext context = null;

    public EventStreamResponse(final HttpResponseStatus status, final ChannelHandler sender,
            final ChannelHandler handler, int sseHeartbeatIntervalMillis) {
        this.status = status;
        this.sender = sender;
        this.handler = handler;
        this.sseHeartbeatIntervalMillis = sseHeartbeatIntervalMillis;
    }

    @Override
    public HttpResponseStatus status() {
        return status;
    }

    public DefaultHttpResponse start(final ChannelHandlerContext ctx, final Integer streamId,
            final HttpVersion version) {
        // Replace handler with the sender and get new context
        context = ctx.channel().pipeline().replace(handler, "tmp", sender).context("tmp");

        final var response = new DefaultHttpResponse(version, HttpResponseStatus.OK);
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        copyStreamId(streamId, response);

        if (sseHeartbeatIntervalMillis > 0) {
            schedulePing();
        }
        return response;
    }

    private void schedulePing() {
        context.executor().schedule(this::sendPing, sseHeartbeatIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private void sendPing() {
        if (isChannelWritable()) {
            context.writeAndFlush(new DefaultHttpContent(PING_MESSAGE.retainedSlice()));
            schedulePing();
        }
    }

    private boolean isChannelWritable() {
        return context != null && !context.isRemoved() && context.channel().isActive();
    }

    static void copyStreamId(final Integer streamId, final HttpMessage to) {
        if (streamId != null) {
            to.headers().setInt(STREAM_ID.text(), streamId);
        }
    }
}
