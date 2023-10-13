/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.errorprone.annotations.DoNotCall;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ChannelSubsystem} bound to a {@link SSHClient} and a Netty channel.
 */
final class TransportClientSubsystem extends ChannelSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(TransportClientSubsystem.class);

    private ChannelHandlerContext pipelineHead;

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

    synchronized OpenFuture open(final TransportChannel underlay) throws IOException {
        LOG.debug("Opening client subsystem \"{}\"", getSubsystem());
        final var openFuture = super.open();
        openFuture.addListener(future -> onOpenComplete(future, underlay));
        return openFuture;
    }

    private void onOpenComplete(final OpenFuture future, final TransportChannel underlay) {
        if (!future.isOpened()) {
            LOG.debug("Failed to open client subsystem \"{}\"", getSubsystem(), future.getException());
            return;
        }

        // Note that there may be multiple handlers already present on the channel, hence we are attaching last, but
        // from the logical perspective we are the head handlers.
        final var pipeline = underlay.channel().pipeline();

        // - install outbound packet handler, i.e. moving bytes from the channel into SSHD's pipeline
        pipeline.addLast(new OutboundChannelHandler(getAsyncIn()));
        // - remember the context of this handler, we will be using it to issue writes into the channel
        pipelineHead = pipeline.lastContext();

        // - install inner channel termination handler
        pipeline.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(final ChannelHandlerContext ctx) throws IOException {
                close();
            }
        });
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
            pipelineHead.fireChannelRead(Unpooled.copiedBuffer(data, off, reqLen));
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
}
