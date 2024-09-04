/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

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
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
final class RestconfSession extends SimpleChannelInboundHandler<FullHttpRequest> implements TransportSession {
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();

    private final RestconfRequestDispatcher dispatcher;

    RestconfSession(final RestconfRequestDispatcher dispatcher) {
        this.dispatcher = requireNonNull(dispatcher);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        dispatcher.dispatch(msg.retain(), new FutureCallback<>() {
            @Override
            public void onSuccess(final FullHttpResponse response) {
                final var streamId = msg.headers().getInt(STREAM_ID);
                if (streamId != null) {
                    response.headers().setInt(STREAM_ID, streamId);
                }
                msg.release();
                ctx.writeAndFlush(response);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                final var message = throwable.getMessage();
                final var content = message == null ? Unpooled.EMPTY_BUFFER
                    : ByteBufUtil.writeUtf8(ctx.alloc(), message);
                final var response = new DefaultFullHttpResponse(msg.protocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
                response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                onSuccess(response);
            }
        });
    }
}
