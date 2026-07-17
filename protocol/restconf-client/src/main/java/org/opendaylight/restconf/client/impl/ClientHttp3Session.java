/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client.impl;

import static org.opendaylight.netconf.transport.http.HTTPClient.getAuthFactory;
import static org.opendaylight.netconf.transport.http.HTTPTransportStack.MAX_HTTP_CONTENT_LENGTH;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.client.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link ClientSession} implementation for HTTP/3.
 *
 * <p>Serves as a gateway to the underlying Netty {@link QuicChannel}. It spawns a new QUIC request stream for
 * every outbound RESTCONF request, mapping the asynchronous responses back to the caller. The
 * {@link Http3FrameToHttpObjectCodec} translates between HTTP/3 frames and HTTP objects, including shutting down
 * the stream output once the request has been written.
 */
public final class ClientHttp3Session extends ClientSession {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp3Session.class);

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        if (!(ctx.channel() instanceof QuicChannel)) {
            throw new IllegalArgumentException(
                "ClientHttp3Session requires a QuicChannel, got " + ctx.channel().getClass());
        }
        super.handlerAdded(ctx);
    }

    @Override
    protected void executeRequest(final @NonNull Channel channel, final @NonNull FullHttpRequest request,
            final @NonNull FutureCallback<FullHttpResponse> callback) {
        Http3.newRequestStream((QuicChannel) channel, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(final QuicStreamChannel streamChannel) {
                final var pipeline = streamChannel.pipeline();
                pipeline.addLast(new Http3FrameToHttpObjectCodec(false));
                pipeline.addLast(new HttpContentDecompressor(0));
                // Generate and attach Auth handler if available
                final var authFactory = getAuthFactory(channel);
                if (authFactory != null) {
                    final var authProvider = authFactory.get();
                    if (authProvider != null) {
                        pipeline.addLast(authProvider);
                    }
                }
                pipeline.addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
                pipeline.addLast(new SingleResponseHandler(callback));
            }
        }).addListener(future -> {
            if (!future.isSuccess()) {
                // Prevent memory leak if stream creation fails
                request.release();
                callback.onFailure(
                    new IOException("Failed to open HTTP/3 stream for request " + request.uri(), future.cause()));
                return;
            }

            final var streamChannel = (QuicStreamChannel) future.getNow();
            streamChannel.writeAndFlush(request).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    LOG.error("Failed to write request to HTTP/3 stream {}", streamChannel.streamId(),
                        writeFuture.cause());
                    streamChannel.pipeline().fireExceptionCaught(writeFuture.cause());
                }
            });
        });
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        // No-op. HTTP/3 request streams handle their own reading.
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        clearChannel();
        super.channelInactive(ctx);
    }
}
