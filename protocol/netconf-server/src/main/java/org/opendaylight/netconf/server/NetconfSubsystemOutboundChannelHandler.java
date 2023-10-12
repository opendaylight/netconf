/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoWriteFuture;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ChannelOutboundHandler responsible for redirecting whatever bytes need to be written out on the Netty channel so
 * that they pass into SSHD's output.
 */
final class NetconfSubsystemOutboundChannelHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSubsystemOutboundChannelHandler.class);

    private final IoOutputStream out;

    NetconfSubsystemOutboundChannelHandler(final IoOutputStream out) {
        this.out = requireNonNull(out);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        // redirect channel outgoing packets to output stream linked to transport
        if (!(msg instanceof ByteBuf byteBuf)) {
            LOG.trace("Ignoring unrecognized {}", msg == null ? null : msg.getClass());
            return;
        }

        // TODO: This can amount to a lot of copying around. Is it worth our while to create a ByteBufBuffer, which
        //       would implement Buffer API on top a ByteBuf?
        //       If we decide to do that, we need to decide to interface with ByteBuf (readRetainedSlice() ?)
        //       and then release it only after the write has been resolved
        final var bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        // Netty buffer can be recycled now
        byteBuf.release();

        final IoWriteFuture writeFuture;
        try {
            writeFuture = out.writeBuffer(new ByteArrayBuffer(bytes));
        } catch (IOException e) {
            LOG.error("Error writing buffer", e);
            promise.setFailure(e);
            return;
        }

        writeFuture.addListener(future -> {
            if (future.isWritten()) {
                // report outbound message being handled
                promise.setSuccess();
            } else if (future.getException() != null) {
                LOG.error("Error writing buffer", future.getException());
                promise.setFailure(future.getException());
            }
        });
    }
}
