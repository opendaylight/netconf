/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ChannelSubsystem} handling the client-side view of a a particular SSH subsystem.
 */
public final class ClientSubsystem extends ChannelSubsystem implements SshFutureListener<OpenFuture> {
    // FIXME: NETCONF-1108: replace this interface with TransportChannelListener
    public interface Initializer {

        void onSuccess(Channel channel);

        void onFailure(Throwable cause);
    }

    private static final Logger LOG = LoggerFactory.getLogger(ClientSubsystem.class);

    // FIXME: NETCONF-1108: do not use EmbeddedChannel!
    // FIXME: NETCONF-1108: we really want to initialize this in operationComplete()
    private final EmbeddedChannel embeddedChannel = new EmbeddedChannel();
    private final Initializer initializer;

    public ClientSubsystem(final String subsystemName, final Initializer callback) {
        super(subsystemName);
        initializer = requireNonNull(callback);
        setStreaming(Streaming.Async);
    }

    @Override
    public synchronized OpenFuture open() throws IOException {
        LOG.debug("opening client subsystem");
        final var openFuture = super.open();
        openFuture.addListener(this);
        return openFuture;
    }

    @Override
    public void operationComplete(final OpenFuture future) {
        final var failure = future.getException();
        if (failure != null) {
            initializer.onFailure(failure);
        } else if (future.isOpened()) {
            // While NETCONF protocol handlers are designed to operate over Netty channel, the inner channel is used to
            // serve NETCONF over SSH. Hence we:
            // - install outbound packet handler, adding fist means it will be invoked last bc of flow direction
            final var pipeline = embeddedChannel.pipeline();
            pipeline.addFirst(new OutboundChannelHandler(getAsyncIn()));

            // - install inner channel termination handler
            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                    close();
                }
            });

            // - let the initializer do its thing
            initializer.onSuccess(embeddedChannel);
            // - and make the channel live
            pipeline.fireChannelActive();
        }
    }

    @Override
    protected void doWriteData(final byte[] data, final int off, final long len) throws IOException {
        // If we're already closing, ignore incoming data
        if (isClosing()) {
            return;
        }
        final int reqLen = (int) len;
        if (reqLen > 0) {
            embeddedChannel.writeInbound(Unpooled.copiedBuffer(data, off, reqLen));
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
    public void close() {
        close(false);
        embeddedChannel.close();
    }
}
