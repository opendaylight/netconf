/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * A {@link Sender} bound directly to the underlying transport channel. This is how event streams are delivered over
 * HTTP/1: there is no provision for executing other requests while the event stream is active.
 *
 * <p>While it might be possible to service requests after the stream has ended (on our side), implementing that would
 * be quite complicated, as we essentially would have to shutdown channel reads while we are sending the stream - except
 * we cannot do that, because TLS underlay needs bidirectional communication for rekeying. Hence we would have to buffer
 * all requests until the stream finishes -- i.e. exerting no backpressure on the client and accumulating state, which
 * is a recipe for a DoS.
 *
 * <p>Hence what this does is drop any request we receive and terminate the connection when the stream ends. That way we
 * are fully compliant with the HTTP specification.
 */
final class ChannelSender extends SimpleChannelInboundHandler<FullHttpRequest> implements Sender {
    private Registration registration;
    private ChannelHandlerContext context;

    ChannelSender() {
        super(FullHttpRequest.class);
    }

    void enable(final Registration reg) {
        registration = requireNonNull(reg);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        // No-op, just drop the message
    }

    @Override
    public void sendDataMessage(final String data) {
        if (!data.isEmpty() && context != null) {
            context.writeAndFlush(ByteBufUtil.writeUtf8(context.alloc(), data));
        }
    }

    @Override
    public void endOfStream() {
        if (registration != null) {
            registration.close();
        }
        if (context != null) {
            context.close();
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        if (context != null) {
            context.close();
        }
        super.channelInactive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        context = ctx;
        super.handlerAdded(ctx);
    }

    public ChannelHandlerContext getCtx() {
        return context;
    }
}
