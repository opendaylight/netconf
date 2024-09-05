/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.AsciiString;

final class DispatcherHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();

    private final OpenApiRequestDispatcher dispatcher;

    DispatcherHandler(final OpenApiRequestDispatcher dispatcher) {
        super(FullHttpRequest.class, false);
        this.dispatcher = requireNonNull(dispatcher);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        // non-null indicates HTTP/2 request, which we need to propagate to any response
        final var streamId = request.headers().getInt(STREAM_ID);
        final var version = request.protocolVersion();

        dispatcher.dispatch(request, new FutureCallback<>() {
            @Override
            public void onSuccess(final FullHttpResponse response) {
                request.release();
                sendResponse(ctx, streamId, response);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                request.release();

                final var message = throwable.getMessage();
                final var content = message == null ? Unpooled.EMPTY_BUFFER
                    : ByteBufUtil.writeUtf8(ctx.alloc(), message);
                final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    content);
                response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

                sendResponse(ctx, streamId, response);
            }
        });
    }

    private static void sendResponse(final ChannelHandlerContext ctx, final Integer streamId,
            final FullHttpResponse response) {
        if (streamId != null) {
            response.headers().setInt(STREAM_ID, streamId);
        }
        ctx.writeAndFlush(response);
    }
}
