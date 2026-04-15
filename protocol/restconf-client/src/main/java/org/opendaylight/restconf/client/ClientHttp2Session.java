/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientHttp2Session extends SimpleChannelInboundHandler<FullHttpResponse>
    implements ClientSession {

    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2Session.class);

    private final Map<Integer, FutureCallback<FullHttpResponse>> map = new ConcurrentHashMap<>();
    private final AtomicInteger streamIdCounter = new AtomicInteger(3);
    private final HTTPScheme scheme;
    private Channel channel;

    public ClientHttp2Session(final HTTPScheme scheme) {
        this.scheme = requireNonNull(scheme);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
    }

    @Override
    public void invoke(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {
        final var local = channel;
        if (local == null) {
            throw new IllegalStateException("Connection is not established yet");
        }

        final var streamId = streamIdCounter.getAndAdd(2);
        request.headers()
            .setInt(ExtensionHeaderNames.STREAM_ID.text(), streamId)
            .set(ExtensionHeaderNames.SCHEME.text(), scheme);

        map.put(streamId, requireNonNull(callback));
        local.writeAndFlush(requireNonNull(request)).addListener(sent -> {
            final var cause = sent.cause();
            if (cause != null && map.remove(streamId, callback)) {
                callback.onFailure(cause);
            }
        });
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        final var streamId = response.headers().getInt(ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            LOG.warn("Unexpected response with no stream ID -- Dropping response object {}", response);
            return;
        }
        final var callback = map.remove(streamId);
        if (callback != null) {
            // Retain the response
            callback.onSuccess(response.retain());
        } else {
            LOG.warn("Unexpected response with unknown or expired stream ID {} -- Dropping response object {}",
                streamId, response);
        }
    }

    public int nextStreamId() {
        // Assuming you have the same streamId counter from the old dispatcher
        return streamIdCounter.getAndAdd(2);
    }
}
