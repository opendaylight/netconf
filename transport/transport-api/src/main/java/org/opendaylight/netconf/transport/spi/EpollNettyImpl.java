/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
final class EpollNettyImpl extends NettyImpl {
    private static final NettyTcpKeepaliveOptions KEEPALIVE_OPTIONS = new NettyTcpKeepaliveOptions(
        EpollChannelOption.TCP_KEEPCNT, EpollChannelOption.TCP_KEEPIDLE, EpollChannelOption.TCP_KEEPINTVL);

    private final IoHandlerFactory ioHandlerFactory = EpollIoHandler.newFactory();

    @Override
    Class<EpollDatagramChannel> datagramChannelClass() {
        return EpollDatagramChannel.class;
    }

    @Override
    Class<EpollSocketChannel> channelClass() {
        return EpollSocketChannel.class;
    }

    @Override
    Class<EpollServerSocketChannel> serverChannelClass() {
        return EpollServerSocketChannel.class;
    }

    @Override
    IoHandlerFactory ioHandlerFactory() {
        return ioHandlerFactory;
    }

    @Override
    @NonNull NettyTcpKeepaliveOptions keepaliveOptions() {
        return KEEPALIVE_OPTIONS;
    }

    @Override
    void setTcpMd5(AbstractBootstrap<?, ?> bootstrap, TcpMd5Secrets secrets) {
        bootstrap.option(EpollChannelOption.TCP_MD5SIG, secrets.toOption());
    }

    @Override
    boolean setTcpMd5(final Channel channel, final TcpMd5Secrets secrets) {
        return channel.config().setOption(EpollChannelOption.TCP_MD5SIG, secrets.toOption());
    }

    @Override
    public String toString() {
        return "epoll(2)";
    }
}