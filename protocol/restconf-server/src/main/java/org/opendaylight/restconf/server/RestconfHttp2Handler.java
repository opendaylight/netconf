/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2Stream;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public final class RestconfHttp2Handler extends Http2EventAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfHttp2Handler.class);

    private final ConcurrentRestconfSession session;

    public RestconfHttp2Handler(final ConcurrentRestconfSession session) {
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public void onStreamAdded(final Http2Stream stream) {
        LOG.info("Stream added: {}", stream.id());
    }

    @Override
    public void onStreamActive(final Http2Stream stream) {
        LOG.info("Stream active: {}", stream.id());
    }

    @Override
    public void onStreamClosed(final Http2Stream stream) {
        LOG.info("Stream closed: {}", stream.id());
    }

    @Override
    public void onStreamRemoved(final Http2Stream stream) {
        LOG.info("Stream removed: {}", stream.id());
    }

    @Override
    public int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data, final int padding,
            final boolean endOfStream) {
        LOG.info("Received DATA on stream #{} with payload: {}", streamId, data.toString(UTF_8));
        if (endOfStream) {
            // The full request body has been received. Now, we can send a response.
            sendResponse(ctx, streamId);
        }
        // We must return the number of bytes consumed.
        int processed = data.readableBytes() + padding;
        return processed;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int padding, final boolean endOfStream) {
        LOG.info("Received HEADERS on stream #{}: {}", streamId, headers);
        if (endOfStream) {
            // This is a request with no body (e.g., a simple GET). Send a response immediately.
            sendResponse(ctx, streamId);
        }
    }

    private void sendResponse(final ChannelHandlerContext ctx, final int streamId) {
        // 1. Create response headers
        Http2Headers headers = new DefaultHttp2Headers().status("200");
        headers.set("content-type", "text/plain; charset=utf-8");
        // 2. Create and write the HEADERS frame
        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers);
        ctx.write(headersFrame);
        // 3. Create and write the DATA frame
        ByteBuf payload = Unpooled.copiedBuffer("Hello, World!", UTF_8);
        // The 'endStream' flag on the last frame must be true to close the stream from our side.
        Http2DataFrame dataFrame = new DefaultHttp2DataFrame(payload, true);
        // Use writeAndFlush() on the last frame to send everything.
        ctx.writeAndFlush(dataFrame);
        System.out.println("Sent response on stream #" + streamId);
        // FIXME send completed requests on the session with 'prepareRequest' and 'respond' methods
    }

    @Override
    public void onRstStreamRead(final ChannelHandlerContext ctx, final int streamId, final long errorCode) {
        System.err.println("Stream #" + streamId + " was reset by the client with error code: " + Http2Error.valueOf(errorCode));
        // Perform cleanup specific to this stream, if any.
        // Note: onStreamClosed() will also be called, so the stream will be removed from our tracking set.
    }
}
