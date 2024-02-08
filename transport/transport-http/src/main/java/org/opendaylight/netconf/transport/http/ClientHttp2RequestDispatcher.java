/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.SCHEME;
import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.STREAM_ID;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.ssl.SslHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link RequestDispatcher} implementation for HTTP 2.
 *
 * <p>
 * Serves as gateway to Netty {@link Channel}, performs sending requests to server, returns server responses associated.
 * Uses request to response mapping by stream identifier.
 */
class ClientHttp2RequestDispatcher extends SimpleChannelInboundHandler<FullHttpResponse> implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2RequestDispatcher.class);

    private final Map<Integer, SettableFuture<FullHttpResponse>> map = new ConcurrentHashMap<>();
    private final AtomicInteger streamIdCounter = new AtomicInteger(3);

    private Channel channel = null;
    private boolean ssl = false;

    ClientHttp2RequestDispatcher() {
        super(true); // auto-release
    }

    private Integer nextStreamId() {
        // identifier for streams initiated from client require to be odd-numbered, 1 is reserved
        // see https://datatracker.ietf.org/doc/html/rfc7540#section-5.1.1
        return streamIdCounter.getAndAdd(2);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        ssl = ctx.pipeline().get(SslHandler.class) != null;
        super.handlerAdded(ctx);
    }

    @Override
    public ListenableFuture<FullHttpResponse> dispatch(final FullHttpRequest request) {
        if (channel == null) {
            throw new IllegalStateException("Connection is not established yet");
        }
        final var streamId = nextStreamId();
        request.headers().setInt(STREAM_ID.text(), streamId);
        request.headers().set(SCHEME.text(), ssl ? HttpScheme.HTTPS.name() : HttpScheme.HTTP.name());

        final var future = SettableFuture.<FullHttpResponse>create();
        channel.writeAndFlush(request).addListener(sent -> {
            if (sent.cause() == null) {
                map.put(streamId, future);
            } else {
                future.setException(sent.cause());
            }
        });
        return future;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        final var streamId = response.headers().getInt(STREAM_ID.text());
        if (streamId == null) {
            LOG.warn("Unexpected response with no stream ID -- Dropping response object {}", response);
            return;
        }
        final var future = map.remove(streamId);
        if (future == null) {
            LOG.warn("Unexpected response with unknown or expired stream ID {} -- Dropping response object {}",
                streamId, response);
            return;
        }
        if (!future.isDone()) {
            // NB using response' copy to disconnect the content data from channel's buffer allocated.
            // this prevents the content data became inaccessible once byte buffer of original message is released
            // on exit of current method
            future.set(response.copy());
        } else {
            LOG.warn("Future is already in Done state -- Dropping response object {}", response);
        }
    }
}
