/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.DoNotCall;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ChannelSubsystem} bound to a {@link SSHClient} and a Netty channel.
 */
final class TransportClientSubsystem extends ChannelSubsystem implements TransportSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(TransportClientSubsystem.class);

    private ChannelHandlerContext head;

    TransportClientSubsystem(final String subsystem) {
        super(subsystem);
        setStreaming(Streaming.Async);
    }

    @Override
    @Deprecated
    @DoNotCall("Always throws UnsupportedOperationException")
    public OpenFuture open() throws IOException {
        throw new UnsupportedOperationException();
    }

    synchronized ListenableFuture<ChannelHandlerContext> open(final TransportChannel underlay) throws IOException {
        LOG.debug("Opening client subsystem \"{}\"", getSubsystem());

        final var ret = SettableFuture.<ChannelHandlerContext>create();
        super.open().addListener(future -> {
            if (future.isOpened()) {
                head = attachToUnderlay(asyncIn, underlay);
                ret.set(head);
            } else {
                final var ex = future.getException();
                LOG.debug("Failed to open client subsystem \"{}\"", getSubsystem(), ex);
                ret.setException(ex);
            }
        });
        return ret;
    }

    @Override
    protected void doWriteData(final byte[] data, final int off, final long len) throws IOException {
        // If we're already closing, ignore incoming data
        if (isClosing()) {
            return;
        }

        final int reqLen = (int) len;
        if (reqLen > 0) {
            LOG.debug("Forwarding {} bytes of data", reqLen);
            head.fireChannelRead(Unpooled.copiedBuffer(data, off, reqLen));
            getLocalWindow().release(reqLen);
        }
    }

    @Override
    protected void doWriteExtendedData(final byte[] data, final int off, final long len) throws IOException {
        // If we're already closing, ignore incoming data
        if (isClosing()) {
            return;
        }
        LOG.debug("Discarding {} bytes of extended data", len);
        if (len > 0) {
            getLocalWindow().release(len);
        }
    }

    @Override
    public void substemChannelInactive() throws IOException {
        close();
    }
}
