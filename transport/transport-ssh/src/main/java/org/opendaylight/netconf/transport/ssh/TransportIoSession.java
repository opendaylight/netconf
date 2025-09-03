/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.SocketAddress;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.ByteArrayBuffer;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoSession;
import org.opendaylight.netconf.shaded.sshd.netty.NettySupport;

final class TransportIoSession implements IoSession {
    private static final Adapter adapter = new Adapter();

    private final IoHandler handler;

    public TransportIoSession(final IoHandler handler) {
        this.handler = handler;
    }

    ChannelInboundHandler handler() {
        // the only thing we need
        return adapter;
    }

    // copy from NettyIoSession
    protected class Adapter extends ChannelInboundHandlerAdapter {

        // Buffer for accumulating ByteBufs if we get multiple read events.
        private ByteArrayBuffer buffer;

        // The ByteBuf from the first (and single) read event, if there's only one. If a second event comes in, this
        // gets copied into buffer, released, and nulled out. This is an optimization to avoid needlessly copying
        // buffers.
        private ByteBuf ioBuffer;

        // Invariant: !(buffer != null && ioBuffer != null) Either they're both null (initially and on read complete),
        // or exactly one of them is non-null.

        public Adapter() {
            super();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // NettyIoSession.this.channelActive(ctx);
            handler.sessionCreated(this);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            buffer = null;
            if (ioBuffer != null) {
                ioBuffer.release();
                ioBuffer = null;
            }
            NettyIoSession.this.channelInactive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            if (buffer == null) {
                if (ioBuffer == null) {
                    // First buffer; will be released in channelReadComplete() below, or when the second ByteBuf
                    // arrives.
                    ioBuffer = buf;
                    return;
                } else {
                    // Second ByteBuf: copy the ioBuffer, release and null it. Then copy buf and release it.
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
            // Clear fields before passing on the buffer, otherwise we might get into trouble if the session causes
            // another read.
            if (buffer != null) {
                ByteArrayBuffer buf = buffer;
                buffer = null;
                NettyIoSession.this.channelRead(ctx, buf);
                // handler.messageReceived(iosession, ...);
            } else if (ioBuffer != null) {
                ByteBuf buf = ioBuffer;
                ioBuffer = null;
                try {
                    NettyIoSession.this.channelRead(ctx, NettySupport.asReadable(buf));
                } finally {
                    buf.release();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            NettyIoSession.this.exceptionCaught(ctx, cause);
        }
    }
}
