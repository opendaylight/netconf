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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import jdk.net.ExtendedSocketOptions;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.common.rev221212.tcp.common.grouping.Keepalives;

@NonNullByDefault
final class NioNettyImpl extends AbstractNettyImpl {
    private static final ChannelOption<Integer> TCP_KEEPIDLE = NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE);
    private static final ChannelOption<Integer> TCP_KEEPCNT = NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPCOUNT);
    private static final ChannelOption<Integer> TCP_KEEPINTVL =
        NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL);

    private final boolean supportsKeepalives;

    NioNettyImpl() {
        final var ch = new NioSocketChannel();
        try {
            supportsKeepalives = ch.config().setOptions(Map.of(
                ChannelOption.SO_KEEPALIVE, Boolean.TRUE, TCP_KEEPIDLE, 7200, TCP_KEEPCNT, 3, TCP_KEEPINTVL, 5));
        } finally {
            ch.close();
        }
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
    EventLoopGroup newEventLoopGroup(final int numThreads, final ThreadFactory threadFactory) {
        return new NioEventLoopGroup(numThreads, threadFactory);
    }

    @Override
    boolean supportsKeepalives() {
        return supportsKeepalives;
    }

    @Override
    void configureKeepalives(final Bootstrap bootstrap, final Keepalives keepalives) {
        bootstrap
            .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
            .option(TCP_KEEPIDLE, keepalives.requireIdleTime().toJava())
            .option(TCP_KEEPCNT, keepalives.requireMaxProbes().toJava())
            .option(TCP_KEEPINTVL, keepalives.requireProbeInterval().toJava());
    }

    @Override
    void configureKeepalives(final ServerBootstrap bootstrap, final Keepalives keepalives) {
        bootstrap
            .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
            .childOption(TCP_KEEPIDLE, keepalives.requireIdleTime().toJava())
            .childOption(TCP_KEEPCNT, keepalives.requireMaxProbes().toJava())
            .childOption(TCP_KEEPINTVL, keepalives.requireProbeInterval().toJava());
    }

    @Override
    public String toString() {
        return "java.nio";
    }
}