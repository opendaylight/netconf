/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Base class for protocol-specific RESTCONF client sessions.
 *
 * <p>Implementations complete the {@link FutureCallback} passed to {@link #invoke} from the transport's Netty I/O
 * thread. Per the {@link SimpleChannelInboundHandler} contract, the {@link FullHttpResponse} is released once the
 * handler that received it returns, so the response handed to {@link FutureCallback#onSuccess(Object)} is only
 * valid for the duration of that call: reading it afterwards, e.g. from the caller's own thread, may see whatever
 * unrelated data now occupies that reclaimed buffer.
 */
public abstract class ClientSession extends SimpleChannelInboundHandler<FullHttpResponse> {
    private volatile Channel channel;

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {
        clearChannel();
        super.handlerRemoved(ctx);
    }

    protected final void clearChannel() {
        channel = null;
    }

    /**
     * Invokes the HTTP request over the established connection.
     *
     * @param request A request to be sent.
     * @param callback A callback for accepting the results. The {@link FullHttpResponse} passed to
     *        {@link FutureCallback#onSuccess(Object)} is only valid for the duration of that call.
     */
    public final void invoke(final @NonNull FullHttpRequest request,
            final @NonNull FutureCallback<FullHttpResponse> callback) {
        final var local = channel;
        if (local != null) {
            executeRequest(local, request, callback);
        } else {
            throw new IllegalStateException("Connection is not established");
        }
    }

    /**
     * Executes the protocol-specific logic to send the request over the channel.
     *
     * @param channel The active Netty channel.
     * @param request The HTTP request to send.
     * @param callback The callback to be notified of the response or failure.
     */
    protected abstract void executeRequest(@NonNull Channel channel, @NonNull FullHttpRequest request,
            @NonNull FutureCallback<FullHttpResponse> callback);
}
