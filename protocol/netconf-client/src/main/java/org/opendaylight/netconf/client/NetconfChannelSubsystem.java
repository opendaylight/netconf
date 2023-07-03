/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Promise;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom implementation of SSHD client {@link ChannelSubsystem}. Serves "netconf" subsystem when SSH transport is used.
 */
public class NetconfChannelSubsystem extends ChannelSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfChannelSubsystem.class);

    private final ClientChannelInitializer channelInitializer;
    private final Promise<NetconfClientSession> promise;

    private EmbeddedChannel embeddedChannel;

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
        embeddedChannel = new EmbeddedChannel() {
            @Override
            protected void handleOutboundMessage(final Object msg) {
                if (msg instanceof ByteBuf byteBuf) {
                    // redirect channel outgoing packets to output stream linked to transport
                    final byte[] bytes = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(bytes);
                    try {
                        getAsyncIn().writeBuffer(new ByteArrayBuffer(bytes))
                            .addListener(future -> {
                                if (future.isWritten()) {
                                    // report outbound message being handled
                                    byteBuf.release();
                                    if (LOG.isTraceEnabled()) {
                                        LOG.trace("Write: {}", new String(bytes, StandardCharsets.UTF_8));
                                    }
                                } else if (future.getException() != null) {
                                    LOG.error("Error writing buffer", future.getException());
                                    NetconfChannelSubsystem.this.close();
                                }
                            });
                    } catch (IOException e) {
                        LOG.error("Error writing buffer", e);
                        NetconfChannelSubsystem.this.close();
                    }
                } else {
                    // non-ByteBuf messages are persisted within channel for subsequent handling
                    super.handleOutboundMessage(msg);
                }
            }
        };

        // inner channel termination handler
        embeddedChannel.pipeline().addFirst(
            new ChannelInboundHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
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
            final var buffer = Unpooled.copiedBuffer(data, off, reqLen);
            embeddedChannel.writeInbound(buffer);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Read: {}", new String(buffer.array(), StandardCharsets.UTF_8));
            }
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
        if (embeddedChannel != null) {
            embeddedChannel.close();
            embeddedChannel = null;
        }
    }
}
