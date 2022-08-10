/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.ThreadFactory;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.common.rev220524.tcp.common.grouping.Keepalives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface providing indirection between various Netty transport implementations.
 */
@NonNullByDefault
public final class NettyTransportSupport {
    private abstract static sealed class AbstractImpl {

        abstract Class<? extends SocketChannel> channelClass();

        abstract Class<? extends ServerSocketChannel> serverChannelClass();

        abstract EventLoopGroup newEventLoopGroup(int numThreads, ThreadFactory threadFactory);

        abstract boolean supportsKeepalives();

        abstract void configureKeepalives(Bootstrap bootstrap, Keepalives keepalives);

        abstract void configureKeepalives(ServerBootstrap bootstrap, Keepalives keepalives);


        @Override
        public abstract String toString();
    }

    private static final class EpollImpl extends AbstractImpl {
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
                .option(EpollChannelOption.SO_KEEPALIVE, Boolean.TRUE)
                .option(EpollChannelOption.TCP_KEEPIDLE, keepalives.requireIdleTime().toJava())
                .option(EpollChannelOption.TCP_KEEPCNT, keepalives.requireMaxProbes().toJava())
                .option(EpollChannelOption.TCP_KEEPINTVL, keepalives.requireProbeInterval().toJava());
        }

        @Override
        void configureKeepalives(final ServerBootstrap bootstrap, final Keepalives keepalives) {
            bootstrap
                .childOption(EpollChannelOption.SO_KEEPALIVE, Boolean.TRUE)
                .childOption(EpollChannelOption.TCP_KEEPIDLE, keepalives.requireIdleTime().toJava())
                .childOption(EpollChannelOption.TCP_KEEPCNT, keepalives.requireMaxProbes().toJava())
                .childOption(EpollChannelOption.TCP_KEEPINTVL, keepalives.requireProbeInterval().toJava());
        }

        @Override
        public String toString() {
            return "epoll(2)";
        }
    }

    private static final class NioImpl extends AbstractImpl {
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
            // FIXME: support keepalives
            return false;
        }

        @Override
        void configureKeepalives(final Bootstrap bootstrap, final Keepalives keepalives) {

        }

        @Override
        void configureKeepalives(final ServerBootstrap bootstrap, final Keepalives keepalives) {

        }

        @Override
        public String toString() {
            return "java.nio";
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NettyTransportSupport.class);
    private static final AbstractImpl IMPL = Epoll.isAvailable() ? new EpollImpl() : new NioImpl();

    static {
        LOG.info("Netty transport backed by {}", IMPL);
    }

    private NettyTransportSupport() {
        // Hidden on purpose
    }

    public static Bootstrap newBootstrap() {
        return new Bootstrap().channel(IMPL.channelClass());
    }

    public static ServerBootstrap newServerBootstrap() {
        return new ServerBootstrap().channel(IMPL.serverChannelClass());
    }

    public static EventLoopGroup newEventLoopGroup(final String name) {
        return newEventLoopGroup(name, 0);
    }

    public static EventLoopGroup newEventLoopGroup(final String name, final int numThreads) {
        return IMPL.newEventLoopGroup(numThreads, new ThreadFactoryBuilder()
            .setNameFormat(requireNonNull(name) + "-%d")
            .setUncaughtExceptionHandler(
                (thread, ex) -> LOG.error("Thread terminated due to uncaught exception: {}", thread.getName(), ex))
            .build());
    }

    static boolean keepalivesSupported() {
        return IMPL.supportsKeepalives();
    }

    static void configureKeepalives(final Bootstrap bootstrap, final Keepalives keepalives)
            throws UnsupportedConfigurationException {
        if (keepalives != null) {
            checkKeepalivesSupported();
            IMPL.configureKeepalives(bootstrap, keepalives);
        }
    }

    static void configureKeepalives(final ServerBootstrap bootstrap, final Keepalives keepalives)
            throws UnsupportedConfigurationException {
        if (keepalives != null) {
            checkKeepalivesSupported();
            IMPL.configureKeepalives(bootstrap, keepalives);
        }
    }

    private static void checkKeepalivesSupported() throws UnsupportedConfigurationException {
        if (!keepalivesSupported()) {
            throw new UnsupportedConfigurationException("Keepalives are not supported");
        }
    }
}
