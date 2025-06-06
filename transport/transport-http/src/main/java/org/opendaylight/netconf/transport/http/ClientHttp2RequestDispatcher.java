/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link RequestDispatcher} implementation for HTTP 2.
 *
 * <p>Serves as gateway to Netty {@link Channel}, performs sending requests to server, returns server responses
 * associated. Uses request to response mapping by stream identifier.
 */
final class ClientHttp2RequestDispatcher extends ClientRequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2RequestDispatcher.class);

    // TODO: we access the queue only from Netty callbacks: can we use a plain HashMap?
    private final Map<Integer, FutureCallback<FullHttpResponse>> map = new ConcurrentHashMap<>();
    // identifier for streams initiated from client require to be odd-numbered, 1 is reserved
    // see https://datatracker.ietf.org/doc/html/rfc7540#section-5.1.1
    private final AtomicInteger streamIdCounter = new AtomicInteger(3);
    private final HTTPScheme scheme;

    ClientHttp2RequestDispatcher(final HTTPScheme scheme) {
        this.scheme = requireNonNull(scheme);
    }

    @Override
    public void dispatch(final Channel channel, final FullHttpRequest request,
            final FutureCallback<FullHttpResponse> callback) {
        final var streamId = nextStreamId();
        request.headers()
            .setInt(ExtensionHeaderNames.STREAM_ID.text(), streamId)
            .set(ExtensionHeaderNames.SCHEME.text(), scheme);

        // Map has to be populated first, simply because a response may arrive sooner than the successful callback
        map.put(streamId, callback);
        channel.writeAndFlush(request).addListener(sent -> {
            final var cause = sent.cause();
            if (cause != null && map.remove(streamId, callback)) {
                callback.onFailure(cause);
            }
        });
    }

    int nextStreamId() {
        return streamIdCounter.getAndAdd(2);
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
            callback.onSuccess(response);
        } else {
            LOG.warn("Unexpected response with unknown or expired stream ID {} -- Dropping response object {}",
                streamId, response);
        }
    }
}
