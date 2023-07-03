/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Promise;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.transport.ssh.OutboundChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom implementation of SSHD client {@link ChannelSubsystem}. Serves "netconf" subsystem when SSH transport is used.
 */
public class NetconfChannelSubsystem extends ChannelSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfChannelSubsystem.class);

    // FIXME: NETCONF-1108: do not use EmbeddedChannel!
    private final EmbeddedChannel embeddedChannel = new EmbeddedChannel();
    private final ClientChannelInitializer channelInitializer;
    private final Promise<NetconfClientSession> promise;

    public NetconfChannelSubsystem(final ClientChannelInitializer channelInitializer,
            final Promise<NetconfClientSession> promise) {
        super("netconf");
        this.channelInitializer = channelInitializer;
        this.promise = promise;
        setStreaming(Streaming.Async);
    }

    @Override
    public synchronized OpenFuture open() throws IOException {
        final var openFuture = super.open();
        openFuture.addListener(future -> {
            final var failure = future.getException();
            if (failure != null) {
                promise.setFailure(failure);
            } else if (future.isOpened()) {
                initInnerChannel();
            }
        });
        LOG.info("opening netconf subsystem channel");
        return openFuture;
    }

    private void initInnerChannel() {
        /*
         * While NETCONF protocol handlers are designed to operate over Netty channel,
         * the inner channel is used to serve NETCONF over SSH.
         */

        // outbound packet handler, adding fist means it will be invoked last bc of flow direction
        embeddedChannel.pipeline().addFirst(new OutboundChannelHandler(getAsyncIn()));

        // inner channel termination handler
        embeddedChannel.pipeline().addLast(
            new ChannelInboundHandlerAdapter() {
                @Override
                public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                    NetconfChannelSubsystem.this.close();
                }
            }
        );

        // NETCONF handlers
        channelInitializer.initialize(embeddedChannel, promise);
        // trigger negotiation flow
        embeddedChannel.pipeline().fireChannelActive();
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
        this.close(false);
        embeddedChannel.close();
    }
}
