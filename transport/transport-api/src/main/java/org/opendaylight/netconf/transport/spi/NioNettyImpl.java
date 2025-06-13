/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.Channel;
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
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

@NonNullByDefault
final class NioNettyImpl extends NettyImpl {
    private static final NettyTcpKeepaliveOptions KEEPALIVE_OPTIONS = new NettyTcpKeepaliveOptions(
        NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPCOUNT),
        NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE),
        NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL));
    private static final boolean SUPPORTS_KEEPALIVES;

    static {
        final var grp = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            try {
                final var ch = new NioSocketChannel();
                grp.register(ch).sync();

                try {
                    SUPPORTS_KEEPALIVES = ch.config().setOptions(Map.of(
                        ChannelOption.SO_KEEPALIVE, Boolean.TRUE,
                        KEEPALIVE_OPTIONS.tcpKeepIdle(), 7200,
                        KEEPALIVE_OPTIONS.tcpKeepCnt(), 3,
                        KEEPALIVE_OPTIONS.tcpKeepIntvl(), 5));
                } finally {
                    ch.close().sync();
                }
            } finally {
                grp.shutdownGracefully().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExceptionInInitializerError(e);
        }
    }

    private final IoHandlerFactory ioHandlerFactory = NioIoHandler.newFactory();
    private final Throwable epollCause;

    NioNettyImpl(final Throwable epollCause) {
        this.epollCause = requireNonNull(epollCause);
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
        return SUPPORTS_KEEPALIVES ? KEEPALIVE_OPTIONS : null;
    }

    @Override
    void setTcpMd5(final AbstractBootstrap<?, ?> bootstrap, final TcpMd5Secrets secrets)
            throws UnsupportedConfigurationException {
        requireNonNull(bootstrap);
        requireNonNull(secrets);
        throw uce();
    }

    @Override
    boolean setTcpMd5(final Channel channel, final TcpMd5Secrets secrets) throws UnsupportedConfigurationException {
        requireNonNull(channel);
        requireNonNull(secrets);
        throw uce();
    }

    @Override
    public String toString() {
        return "java.nio " + (SUPPORTS_KEEPALIVES ? "with" : "without") + " TCP keepalive options";
    }

    private UnsupportedConfigurationException uce() {
        return new UnsupportedConfigurationException("TCP MD5 Signatures are not supported", epollCause.getCause());
    }
}