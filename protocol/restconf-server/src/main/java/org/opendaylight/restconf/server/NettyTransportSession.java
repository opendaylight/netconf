/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.AsciiString;
import java.nio.charset.StandardCharsets;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * A {@link TransportSession} attached to a transport channel.
 */
final class NettyTransportSession extends SimpleChannelInboundHandler<FullHttpRequest> implements TransportSession {
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();

    private final RestconfRequestDispatcher dispatcher;

    NettyTransportSession(final RestconfRequestDispatcher dispatcher) {
        super(FullHttpRequest.class, false);
        this.dispatcher = requireNonNull(dispatcher);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        dispatcher.dispatch(msg, new FutureCallback<>() {
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
                    : Unpooled.wrappedBuffer(message.getBytes(StandardCharsets.UTF_8));
                final var response = new DefaultFullHttpResponse(msg.protocolVersion(), INTERNAL_SERVER_ERROR, content);
                response.headers()
                    .set(CONTENT_TYPE, TEXT_PLAIN)
                    .setInt(CONTENT_LENGTH, response.content().readableBytes());
                onSuccess(response);
            }
        });
    }
}
