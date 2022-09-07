/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;
import java.net.SocketAddress;
import org.opendaylight.netconf.shaded.sshd.common.session.helpers.AbstractSession;

/**
 * @author nite
 *
 */
final class SSHHandler extends ChannelInboundHandlerAdapter implements ChannelOutboundHandler {
    private final AbstractSession session;




    @Override
    public void bind(final ChannelHandlerContext ctx, final SocketAddress localAddress, final ChannelPromise promise) {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(final ChannelHandlerContext ctx, final SocketAddress remoteAddress,
            final SocketAddress localAddress, final ChannelPromise promise) {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void deregister(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        ctx.deregister(promise);
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        // TODO Auto-generated method stub
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public void read(final ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof ByteBuf buf) {
            // FIXME: feed into SSH channel
        } else {
            final var ex = new UnsupportedMessageTypeException(msg, ByteBuf.class);
            ReferenceCountUtil.safeRelease(msg);
            promise.setFailure(ex);
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        // FIXME: flush any pending stuff
    }
}
