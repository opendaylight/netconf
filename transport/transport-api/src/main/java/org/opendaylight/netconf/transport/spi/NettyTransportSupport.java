/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import java.util.concurrent.ThreadFactory;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.slf4j.LoggerFactory;

/**
 * Utility class providing indirection between various Netty transport implementations. An implementation is chosen
 * based on run-time characteristics -- either {@code epoll(2)}-based or {@code java.nio}-based one.
 */
@Beta
@NonNullByDefault
public final class NettyTransportSupport {
    private static final NettyImpl IMPL;

    static {
        final var epollCause = Epoll.unavailabilityCause();
        IMPL = epollCause == null ? new EpollNettyImpl() : new NioNettyImpl(epollCause);
        LoggerFactory.getLogger(NettyTransportSupport.class).info("Netty transport backed by {}", IMPL);
    }

    private NettyTransportSupport() {
        // Hidden on purpose
    }

    /**
     * Return a new {@link Bootstrap} instance. The bootstrap has its {@link Bootstrap#channel(Class)} already
     * initialized to the backing implementation's {@link SocketChannel} class.
     *
     * @return A new {@link Bootstrap}
     */
    public static Bootstrap newBootstrap() {
        return new Bootstrap().channel(IMPL.channelClass());
    }

    /**
     * Return a new {@link Bootstrap} instance. The bootstrap has its {@link Bootstrap#channel(Class)} already
     * initialized to the backing implementation's {@link DatagramChannel} class.
     *
     * @return A new Bootstrap
     */
    public static Bootstrap newDatagramBootstrap() {
        return new Bootstrap().channel(IMPL.datagramChannelClass());
    }

    /**
     * Return a new {@link ServerBootstrap} instance. The bootstrap has its {@link ServerBootstrap#channel(Class)}
     * already initialized to the backing implementation's {@link ServerSocketChannel} class.
     *
     * @return A new {@link ServerBootstrap}
     */
    public static ServerBootstrap newServerBootstrap() {
        return new ServerBootstrap().channel(IMPL.serverChannelClass());
    }

    /**
     * Create a new {@link EventLoopGroup} supporting the backing implementation with specified (or default) number of
     * threads.
     *
     * @param threadFactory {@link ThreadFactory} to use to create threads
     * @param numThreads Number of threads in the group, must be non-negative. Zero selects the default.
     * @return An EventLoopGroup
     */
    public static EventLoopGroup newEventLoopGroup(final int numThreads, final ThreadFactory threadFactory) {
        return new MultiThreadIoEventLoopGroup(numThreads, requireNonNull(threadFactory), IMPL.ioHandlerFactory());
    }

    /**
     * Return a {@link NettyTcpKeepaliveOptions} descriptor, or {@code null} if TCP keepalives are not configurable.
     *
     * @return A {@link NettyTcpKeepaliveOptions} or {@code null}
     */
    public static @Nullable NettyTcpKeepaliveOptions tcpKeepaliveOptions() {
        return IMPL.keepaliveOptions();
    }

    /**
     * Return a {@link NettyTcpKeepaliveOptions} descriptor.
     *
     * @return A {@link NettyTcpKeepaliveOptions}
     * @throws UnsupportedConfigurationException if TCP keepalives are not configurable
     */
    public static NettyTcpKeepaliveOptions getTcpKeepaliveOptions() throws UnsupportedConfigurationException {
        final var options = tcpKeepaliveOptions();
        if (options == null) {
            throw new UnsupportedConfigurationException("TCP keepalives are not supported");
        }
        return options;
    }

    /**
     * Configure a bootstrap to use {@code TCP MD5 Signature} option with specified {@link TcpMd5Secrets}.
     *
     * @param bootstrap the bootstrap
     * @param secrets the {@link TcpMd5Secrets} to set
     * @throws UnsupportedConfigurationException if {@code TCP MD5 Signature} option is not supported
     */
    public static void setTcpMd5(final AbstractBootstrap<?, ?> bootstrap, final TcpMd5Secrets secrets)
            throws UnsupportedConfigurationException {
        IMPL.setTcpMd5(bootstrap, secrets);
    }

    /**
     * Configure a {@link Channel} to use {@code TCP MD5 Signature} option with specified {@link TcpMd5Secrets}.
     *
     * @param channel the channel
     * @param secrets the {@link TcpMd5Secrets} to set
     * @return boolean {@code true} if the channel has been updated successfully
     * @throws UnsupportedConfigurationException if {@code TCP MD5 Signature} option is not supported
     */
    public static boolean setTcpMd5(final Channel channel, final TcpMd5Secrets secrets)
            throws UnsupportedConfigurationException {
        return IMPL.setTcpMd5(channel, secrets);
    }
}
