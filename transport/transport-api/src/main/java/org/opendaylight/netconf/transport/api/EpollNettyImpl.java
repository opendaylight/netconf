/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import java.util.concurrent.ThreadFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
final class EpollNettyImpl extends NettyImpl {
    private static final TcpKeepaliveOptions KEEPALIVE_OPTIONS = new TcpKeepaliveOptions(
        EpollChannelOption.TCP_KEEPCNT, EpollChannelOption.TCP_KEEPIDLE, EpollChannelOption.TCP_KEEPINTVL);

    @Override
    public Class<EpollDatagramChannel> datagramChannelClass() {
        return EpollDatagramChannel.class;
    }

    @Override
    public Class<EpollSocketChannel> channelClass() {
        return EpollSocketChannel.class;
    }

    @Override
    public Class<EpollServerSocketChannel> serverChannelClass() {
        return EpollServerSocketChannel.class;
    }

    @Override
    public EventLoopGroup newEventLoopGroup(final int numThreads, final ThreadFactory threadFactory) {
        return new EpollEventLoopGroup(numThreads, threadFactory);
    }

    @Override
    public @NonNull TcpKeepaliveOptions keepaliveOptions() {
        return KEEPALIVE_OPTIONS;
    }

    @Override
    public String toString() {
        return "epoll(2)";
    }
}