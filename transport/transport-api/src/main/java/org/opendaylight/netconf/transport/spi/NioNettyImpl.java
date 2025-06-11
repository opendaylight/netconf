/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import io.netty.channel.ChannelOption;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.Map;
import jdk.net.ExtendedSocketOptions;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
final class NioNettyImpl extends NettyImpl {
    private static final NettyTcpKeepaliveOptions KEEPALIVE_OPTIONS = new NettyTcpKeepaliveOptions(
        NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPCOUNT),
        NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE),
        NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL));

    static final NioNettyImpl INSTANCE;

    static {
        final var grp = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            try {
                final var ch = new NioSocketChannel();
                grp.register(ch).sync();

                final boolean supportsKeepalives;
                try {
                    supportsKeepalives = ch.config().setOptions(Map.of(
                        ChannelOption.SO_KEEPALIVE, Boolean.TRUE,
                        KEEPALIVE_OPTIONS.tcpKeepIdle(), 7200,
                        KEEPALIVE_OPTIONS.tcpKeepCnt(), 3,
                        KEEPALIVE_OPTIONS.tcpKeepIntvl(), 5));
                } finally {
                    ch.close().sync();
                }
                INSTANCE = new NioNettyImpl(supportsKeepalives);
            } finally {
                grp.shutdownGracefully().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExceptionInInitializerError(e);
        }
    }

    private final IoHandlerFactory ioHandlerFactory = NioIoHandler.newFactory();
    private final boolean supportsKeepalives;

    private NioNettyImpl(final boolean supportsKeepalives) {
        this.supportsKeepalives = supportsKeepalives;
    }

    @Override
    Class<NioDatagramChannel> datagramChannelClass() {
        return NioDatagramChannel.class;
    }

    @Override
    Class<NioSocketChannel> channelClass() {
        return NioSocketChannel.class;
    }

    @Override
    Class<NioServerSocketChannel> serverChannelClass() {
        return NioServerSocketChannel.class;
    }

    @Override
    IoHandlerFactory ioHandlerFactory() {
        return ioHandlerFactory;
    }

    @Override
    @Nullable NettyTcpKeepaliveOptions keepaliveOptions() {
        return supportsKeepalives ? KEEPALIVE_OPTIONS : null;
    }

    @Override
    public String toString() {
        return "java.nio";
    }
}