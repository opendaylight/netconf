/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class providing indirection between various Netty transport implementations. An implementation is chosen
 * based on run-time characteristics -- either {@code epoll(2)}-based or {@code java.nio}-based one.
 */
@NonNullByDefault
public final class NettyTransportSupport {
    private static final Logger LOG = LoggerFactory.getLogger(NettyTransportSupport.class);

    private NettyTransportSupport() {
        // Hidden on purpose
    }

    /**
     * Return a new {@link Bootstrap} instance. The bootstrap has its {@link Bootstrap#channel(Class)} already
     * initialized to the backing implementation's {@link SocketChannel} class.
     *
     * @return A new Bootstrap
     * @deprecated Use {@link org.opendaylight.netconf.transport.spi.NettyTransportSupport#newBootstrap()} instead.
     */
    @Deprecated(since = "8.0.3", forRemoval = true)
    public static Bootstrap newBootstrap() {
        return org.opendaylight.netconf.transport.spi.NettyTransportSupport.newBootstrap();
    }

    /**
     * Return a new {@link ServerBootstrap} instance. The bootstrap has its {@link ServerBootstrap#channel(Class)}
     * already initialized to the backing implementation's {@link ServerSocketChannel} class.
     *
     * @return A new ServerBootstrap
     * @deprecated Use {@link org.opendaylight.netconf.transport.spi.NettyTransportSupport#newServerBootstrap()}
     *             instead.
     */
    @Deprecated(since = "8.0.3", forRemoval = true)
    public static ServerBootstrap newServerBootstrap() {
        return org.opendaylight.netconf.transport.spi.NettyTransportSupport.newServerBootstrap();
    }

    /**
     * Create a new {@link EventLoopGroup} supporting the backing implementation with default number of threads. The
     * default is twice the number of available processors, or controlled through the {@code io.netty.eventLoopThreads}
     * system property.
     *
     * @param name Thread naming prefix
     * @return An EventLoopGroup
     */
    public static EventLoopGroup newEventLoopGroup(final String name) {
        return newEventLoopGroup(name, 0);
    }

    /**
     * Create a new {@link EventLoopGroup} supporting the backing implementation with specified (or default) number of
     * threads.
     *
     * @param name Thread naming prefix
     * @param numThreads Number of threads in the group, must be non-negative. Zero selects the default.
     * @return An EventLoopGroup
     */
    public static EventLoopGroup newEventLoopGroup(final String name, final int numThreads) {
        return org.opendaylight.netconf.transport.spi.NettyTransportSupport.newEventLoopGroup(numThreads,
            Thread.ofPlatform()
                .name(requireNonNull(name) + '-', 0)
                .uncaughtExceptionHandler((thread, ex) ->
                    LOG.error("Thread terminated due to uncaught exception: {}", thread.getName(), ex))
                .factory());
    }
}
