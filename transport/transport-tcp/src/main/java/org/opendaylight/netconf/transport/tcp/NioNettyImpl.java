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
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import jdk.net.ExtendedSocketOptions;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.common.rev230417.tcp.common.grouping.Keepalives;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class NioNettyImpl extends AbstractNettyImpl {
    // FIXME: all of this is a workaround for https://issues.apache.org/jira/browse/KARAF-7690
    private abstract static class AbstractSupport {

        abstract boolean configureKeepalives(SocketChannelConfig config);

        abstract void configureKeepalives(ServerBootstrap bootstrap, Keepalives keepalives);

        abstract void configureKeepalives(Bootstrap bootstrap, Keepalives keepalives);
    }

    private static final class Supported extends AbstractSupport {
        private static final ChannelOption<Integer> TCP_KEEPIDLE =
            NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE);
        private static final ChannelOption<Integer> TCP_KEEPCNT =
            NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPCOUNT);
        private static final ChannelOption<Integer> TCP_KEEPINTVL =
            NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL);

        @Override
        boolean configureKeepalives(final SocketChannelConfig config) {
            return config.setOptions(Map.of(
                ChannelOption.SO_KEEPALIVE, Boolean.TRUE, TCP_KEEPIDLE, 7200, TCP_KEEPCNT, 3, TCP_KEEPINTVL, 5));
        }

        @Override
        void configureKeepalives(final ServerBootstrap bootstrap, final Keepalives keepalives) {
            bootstrap
                .childOption(TCP_KEEPIDLE, keepalives.requireIdleTime().toJava())
                .childOption(TCP_KEEPCNT, keepalives.requireMaxProbes().toJava())
                .childOption(TCP_KEEPINTVL, keepalives.requireProbeInterval().toJava());
        }

        @Override
        void configureKeepalives(final Bootstrap bootstrap, final Keepalives keepalives) {
            bootstrap
                .option(TCP_KEEPIDLE, keepalives.requireIdleTime().toJava())
                .option(TCP_KEEPCNT, keepalives.requireMaxProbes().toJava())
                .option(TCP_KEEPINTVL, keepalives.requireProbeInterval().toJava());
        }
    }

    private static final class Unsupported extends AbstractSupport {
        @Override
        boolean configureKeepalives(final SocketChannelConfig config) {
            return false;
        }

        @Override
        void configureKeepalives(final ServerBootstrap bootstrap, final Keepalives keepalives) {
            // Noop
        }

        @Override
        void configureKeepalives(final Bootstrap bootstrap, final Keepalives keepalives) {
            // Noop
        }
    }

    private static final AbstractSupport SUPPORT;

    static {
        AbstractSupport support;

        try {
            support = new Supported();
        } catch (NoClassDefFoundError e) {
            LoggerFactory.getLogger(NioNettyImpl.class).warn("Disabling keepalive support", e);
            support = new Unsupported();
        }

        SUPPORT = support;
    }

    static final NioNettyImpl INSTANCE;

    static {
        final var grp = new NioEventLoopGroup();
        try {
            try {
                final var ch = new NioSocketChannel();
                grp.register(ch).sync();

                final boolean supportsKeepalives;
                try {
                    supportsKeepalives = SUPPORT.configureKeepalives(ch.config());
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

    private final boolean supportsKeepalives;

    private NioNettyImpl(final boolean supportsKeepalives) {
        this.supportsKeepalives = supportsKeepalives;
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
        SUPPORT.configureKeepalives(bootstrap.option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE), keepalives);
    }

    @Override
    void configureKeepalives(final ServerBootstrap bootstrap, final Keepalives keepalives) {
        SUPPORT.configureKeepalives(bootstrap.childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE), keepalives);
    }

    @Override
    public String toString() {
        return "java.nio";
    }
}