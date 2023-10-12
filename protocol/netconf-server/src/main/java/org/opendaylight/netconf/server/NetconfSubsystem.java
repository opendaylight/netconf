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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.shaded.sshd.common.io.IoInputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.ByteArrayBuffer;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelDataReceiver;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelSession;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelSessionAware;
import org.opendaylight.netconf.shaded.sshd.server.command.AbstractCommandSupport;
import org.opendaylight.netconf.shaded.sshd.server.command.AsyncCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NetconfSubsystem extends AbstractCommandSupport implements AsyncCommand, ChannelSessionAware {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSubsystem.class);

    private final EmbeddedChannel innerChannel = new EmbeddedChannel();
    private final ServerChannelInitializer channelInitializer;

    private IoOutputStream ioOutputStream;

    NetconfSubsystem(final String name, final ServerChannelInitializer channelInitializer) {
        super(name, null);
        this.channelInitializer = requireNonNull(channelInitializer);
    }

    @Override
    public void run() {
        // not used
    }

    @Override
    public void setIoInputStream(final IoInputStream in) {
        // not used
    }

    @Override
    public void setIoErrorStream(final IoOutputStream err) {
        // not used
    }

    @Override
    public void setIoOutputStream(final IoOutputStream out) {
        ioOutputStream = out;

        /*
         * While NETCONF protocol handlers are designed to operate over Netty channel, the inner channel is used to
         * serve NETCONF over SSH.
         */
        // outbound packet handler, adding fist means it will be invoked last because of flow direction
        innerChannel.pipeline().addFirst(
            new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
                    if (msg instanceof ByteBuf byteBuf) {
                        // redirect channel outgoing packets to output stream linked to transport
                        final byte[] bytes = new byte[byteBuf.readableBytes()];
                        byteBuf.readBytes(bytes);
                        try {
                            ioOutputStream.writeBuffer(new ByteArrayBuffer(bytes))
                                .addListener(future -> {
                                    if (future.isWritten()) {
                                        byteBuf.release(); // report outbound message being handled
                                        promise.setSuccess();
                                    } else if (future.getException() != null) {
                                        LOG.error("Error writing buffer", future.getException());
                                        promise.setFailure(future.getException());
                                    }
                                });
                        } catch (IOException e) {
                            LOG.error("Error writing buffer", e);
                        }
                    }
                }
            });

        // inner channel termination handler
        innerChannel.pipeline().addLast(
            new ChannelInboundHandlerAdapter() {
                @Override
                public void channelInactive(final ChannelHandlerContext ctx) {
                    onExit(0);
                }
            });

        // NETCONF protocol handlers
        channelInitializer.initialize(innerChannel, new DefaultPromise<>(GlobalEventExecutor.INSTANCE));
        // trigger negotiation flow
        innerChannel.pipeline().fireChannelActive();
        // set additional info for upcoming netconf session
        innerChannel.writeInbound(Unpooled.wrappedBuffer(getHelloAdditionalMessageBytes()));
    }

    @Override
    public void setChannelSession(final ChannelSession channelSession) {
        /*
         * Inbound packets handler
         * NOTE: The channel data receiver require to be set within current method, so it could be handled
         * with subsequent logic of ChannelSession#prepareChannelCommand() where this method is executed from.
         */
        channelSession.setDataReceiver(new ChannelDataReceiver() {
            @Override
            public int data(final ChannelSession channel, final byte[] buf, final int start, final int len) {
                innerChannel.writeInbound(Unpooled.copiedBuffer(buf, start, len));
                return len;
            }

            @Override
            public void close() {
                innerChannel.close();
            }
        });
    }

    @Override
    protected void onExit(final int exitValue, final String exitMessage) {
        super.onExit(exitValue, exitMessage);
        innerChannel.close();
    }

    private byte[] getHelloAdditionalMessageBytes() {
        final var session = getServerSession();
        final var address = (InetSocketAddress) session.getClientAddress();
        return new NetconfHelloMessageAdditionalHeader(session.getUsername(), address.getAddress().getHostAddress(),
            String.valueOf(address.getPort()), "ssh", "client")
            .toFormattedString().getBytes(StandardCharsets.UTF_8);
    }
}