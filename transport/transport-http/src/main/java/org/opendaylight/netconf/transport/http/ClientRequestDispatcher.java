/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.eclipse.jdt.annotation.NonNull;

abstract class ClientRequestDispatcher extends SimpleChannelInboundHandler<FullHttpResponse>
        implements RequestDispatcher {
    private Channel channel = null;

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        super.handlerAdded(ctx);
    }

    @Override
    public final void dispatch(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {
        final var local = channel;
        if (local != null) {
            dispatch(local, requireNonNull(request), requireNonNull(callback));
        } else {
            throw new IllegalStateException("Connection is not established yet");
        }
    }

    abstract void dispatch(@NonNull Channel channel, @NonNull FullHttpRequest request,
        @NonNull FutureCallback<FullHttpResponse> callback);
}
