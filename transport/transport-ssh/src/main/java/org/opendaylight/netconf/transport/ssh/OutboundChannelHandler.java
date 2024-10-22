/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.io.IOException;
import java.util.ArrayDeque;
import org.opendaylight.netconf.shaded.sshd.common.channel.ChannelAsyncOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoWriteFuture;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.ByteArrayBuffer;
import org.opendaylight.netconf.shaded.sshd.common.util.io.functors.IOFunction;
import org.opendaylight.netconf.shaded.sshd.common.util.threads.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ChannelOutboundHandler} responsible for redirecting whatever bytes need to be written out on the Netty
 * channel so that they pass into SSHD's output.
 *
 * <p>This class is specialized for {@link ChannelAsyncOutputStream} on purpose, as this handler is invoked from the
 * Netty thread and we do not want to block those. We therefore rely on {@link ChannelAsyncOutputStream}'s
 * single-async-write promise and perform queueing here.
 */
final class OutboundChannelHandler extends ChannelOutboundHandlerAdapter {
    // A write enqueued in pending queue
    private record Write(ByteBuf buf, ChannelPromise promise) {
        Write {
            requireNonNull(buf);
            requireNonNull(promise);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(OutboundChannelHandler.class);

    private final IOFunction<ByteArrayBuffer, IoWriteFuture> outWriteBuffer;

    // write requests that need to be sent once currently-outstanding write completes
    private ArrayDeque<Write> pending;
    // indicates we have an outstanding write
    private boolean writePending;

    OutboundChannelHandler(final ChannelAsyncOutputStream out) {
        outWriteBuffer = requireNonNull(out)::writeBuffer;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        // redirect channel outgoing packets to output stream linked to transport
        if (msg instanceof ByteBuf buf) {
            write(buf, promise);
        } else {
            LOG.trace("Ignoring unrecognized {}", msg == null ? null : msg.getClass());
        }
    }

    private void write(final ByteBuf buf, final ChannelPromise promise) {
        if (writePending) {
            LOG.trace("A write is already pending, delaying write");
            delayWrite(buf, promise);
        } else {
            LOG.trace("Issuing immediate write");
            startWrite(buf, promise);
        }
    }

    private void delayWrite(final ByteBuf buf, final ChannelPromise promise) {
        if (pending == null) {
            // these are per-session, hence we want to start out small
            pending = new ArrayDeque<>(1);
        }
        pending.addLast(new Write(buf, promise));
    }

    private void startWrite(final ByteBuf buf, final ChannelPromise promise) {
        final var sshBuf = toSshBuffer(buf);

        final IoWriteFuture writeFuture;
        try {
            // Note: we may end up in KeyExchangeMessageHandler if a key exchange is ongoing -- and it must not block
            //       us, as we *might* be executing on the Netty IO thread. Hence we mark ourselves as internal thread.
            // TODO: this is quite expensive, hence at some point we want to have an aligned stack so we can end up
            //       being blocked
            writeFuture = ThreadUtils.runAsInternal(sshBuf, outWriteBuffer);
        } catch (IOException e) {
            failWrites(promise, e);
            return;
        }

        writePending = true;
        writeFuture.addListener(future -> finishWrite(future, promise));
    }

    private void finishWrite(final IoWriteFuture future, final ChannelPromise promise) {
        writePending = false;

        if (future.isWritten()) {
            // report outbound message being handled
            promise.setSuccess();

            if (pending != null) {
                // TODO: here we could be coalescing multiple ByteBufs into a single Buffer
                final var next = pending.pollFirst();
                if (next != null) {
                    LOG.trace("Issuing next write");
                    startWrite(next.buf, next.promise);
                }
            }
            return;
        }

        final var cause = future.getException();
        if (cause != null) {
            failWrites(promise, cause);
        }
    }

    private void failWrites(final ChannelPromise promise, final Throwable cause) {
        LOG.error("Error writing buffer", cause);
        promise.setFailure(cause);

        // Cascade to all delayed messages
        if (pending != null) {
            pending.forEach(msg -> msg.promise.setFailure(cause));
            pending = null;
        }
    }

    // TODO: This can amount to a lot of copying around. Is it worth our while to create a ByteBufBuffer, which
    //       would implement Buffer API on top a ByteBuf?
    //       If we decide to do that, we need to decide to interface with ByteBuf (readRetainedSlice() ?) and then
    //       release it only after the write has been resolved
    private static ByteArrayBuffer toSshBuffer(final ByteBuf byteBuf) {
        final var bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        // Netty buffer can be recycled now
        byteBuf.release();
        return new ByteArrayBuffer(bytes);
    }
}
