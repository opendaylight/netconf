/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.ByteArrayBuffer;
import org.opendaylight.netconf.shaded.sshd.netty.NettySupport;

/**
 * Handler implementation based on {@link org.opendaylight.netconf.shaded.sshd.netty.NettyIoSession.Adapter}.
 */
final class OdlFlowControlHandler extends ChannelInboundHandlerAdapter {
    private final TransportIoSession session;
    private final IoHandler ioHandler;

    private ByteArrayBuffer buffer;
    private ByteBuf ioBuffer;

    OdlFlowControlHandler(final @NonNull IoHandler ioHandler, final @NonNull TransportIoSession session) {
        this.ioHandler = requireNonNull(ioHandler);
        this.session = requireNonNull(session);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        session.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        buffer = null;
        if (ioBuffer != null) {
            ioBuffer.release();
            ioBuffer = null;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        if (buffer == null) {
            if (ioBuffer == null) {
                ioBuffer = buf;
            } else {
                try {
                    buffer = new ByteArrayBuffer(ioBuffer.readableBytes() + buf.readableBytes(), false);
                    buffer.putBuffer(NettySupport.asReadable(ioBuffer), false);
                    buffer.putBuffer(NettySupport.asReadable(buf), false);
                } finally {
                    ioBuffer.release();
                    ioBuffer = null;
                    buf.release();
                }
            }
        } else {
            try {
                buffer.putBuffer(NettySupport.asReadable(buf), true);
            } finally {
                buf.release();
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (buffer != null) {
            ByteArrayBuffer buf = buffer;
            buffer = null;
            ioHandler.messageReceived(session, buf);
        } else if (ioBuffer != null) {
            ByteBuf buf = ioBuffer;
            ioBuffer = null;
            try {
                ioHandler.messageReceived(session, NettySupport.asReadable(buf));
            } finally {
                buf.release();
            }
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        ioHandler.exceptionCaught(session, cause);
    }
}
