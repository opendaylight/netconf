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
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.common.rev230417.tcp.common.grouping.Keepalives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class providing indirection between various Netty transport implementations. An implementation is chosen
 * based on run-time characteristics -- either {@code epoll(2)}-based or {@code java.nio}-based one.
 */
@NonNullByDefault
public final class NettyTransportSupport {
    private static final Logger LOG = LoggerFactory.getLogger(NettyTransportSupport.class);
    private static final AbstractNettyImpl IMPL = Epoll.isAvailable() ? new EpollNettyImpl() : NioNettyImpl.INSTANCE;

    static {
        LOG.info("Netty transport backed by {}", IMPL);
    }

    private NettyTransportSupport() {
        // Hidden on purpose
    }

    /**
     * Return a new {@link Bootstrap} instance. The bootstrap has its {@link Bootstrap#channel(Class)} already
     * initialized to the backing implementation's {@link SocketChannel} class.
     *
     * @return A new Bootstrap
     */
    public static Bootstrap newBootstrap() {
        return new Bootstrap().channel(IMPL.channelClass());
    }

    /**
     * Return a new {@link Bootstrap} instance. The bootstrap has its {@link ServerBootstrap#channel(Class)} already
     * initialized to the backing implementation's {@link ServerSocketChannel} class.
     *
     * @return A new ServerBootstrap
     */
    public static ServerBootstrap newServerBootstrap() {
        return new ServerBootstrap().channel(IMPL.serverChannelClass());
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
        return IMPL.newEventLoopGroup(numThreads, new ThreadFactoryBuilder()
            .setNameFormat(requireNonNull(name) + "-%d")
            .setUncaughtExceptionHandler(
                (thread, ex) -> LOG.error("Thread terminated due to uncaught exception: {}", thread.getName(), ex))
            .build());
    }

    static boolean keepalivesSupported() {
        return IMPL.supportsKeepalives();
    }

    static void configureKeepalives(final Bootstrap bootstrap, final @Nullable Keepalives keepalives)
            throws UnsupportedConfigurationException {
        if (keepalives != null) {
            checkKeepalivesSupported();
            IMPL.configureKeepalives(bootstrap, keepalives);
        }
    }

    static void configureKeepalives(final ServerBootstrap bootstrap, final @Nullable Keepalives keepalives)
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
