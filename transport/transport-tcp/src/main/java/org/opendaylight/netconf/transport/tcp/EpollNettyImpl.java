/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import java.util.concurrent.ThreadFactory;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.common.rev230417.tcp.common.grouping.Keepalives;

@NonNullByDefault
final class EpollNettyImpl extends AbstractNettyImpl {
    @Override
    Class<EpollSocketChannel> channelClass() {
        return EpollSocketChannel.class;
    }

    @Override
    Class<EpollServerSocketChannel> serverChannelClass() {
        return EpollServerSocketChannel.class;
    }

    @Override
    EventLoopGroup newEventLoopGroup(final int numThreads, final ThreadFactory threadFactory) {
        return new EpollEventLoopGroup(numThreads, threadFactory);
    }

    @Override
    boolean supportsKeepalives() {
        return true;
    }

    @Override
    void configureKeepalives(final Bootstrap bootstrap, final Keepalives keepalives) {
        bootstrap
            .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
            .option(EpollChannelOption.TCP_KEEPIDLE, keepalives.requireIdleTime().toJava())
            .option(EpollChannelOption.TCP_KEEPCNT, keepalives.requireMaxProbes().toJava())
            .option(EpollChannelOption.TCP_KEEPINTVL, keepalives.requireProbeInterval().toJava());
    }

    @Override
    void configureKeepalives(final ServerBootstrap bootstrap, final Keepalives keepalives) {
        bootstrap
            .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
            .childOption(EpollChannelOption.TCP_KEEPIDLE, keepalives.requireIdleTime().toJava())
            .childOption(EpollChannelOption.TCP_KEEPCNT, keepalives.requireMaxProbes().toJava())
            .childOption(EpollChannelOption.TCP_KEEPINTVL, keepalives.requireProbeInterval().toJava());
    }

    @Override
    public String toString() {
        return "epoll(2)";
    }
}