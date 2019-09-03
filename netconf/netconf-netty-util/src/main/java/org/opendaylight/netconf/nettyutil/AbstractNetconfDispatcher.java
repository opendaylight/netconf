/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatcher class for creating servers and clients. The idea is to first create servers and clients and the run the
 * start method that will handle sockets in different thread.
 */
@Deprecated
public abstract class AbstractNetconfDispatcher<S extends NetconfSession, L extends NetconfSessionListener<? super S>>
        implements Closeable {

    protected interface ChannelPipelineInitializer<C extends Channel, S extends NetconfSession> {
        /**
         * Initializes channel by specifying the handlers in its pipeline. Handlers are protocol specific, therefore
         * this method needs to be implemented in protocol specific Dispatchers.
         *
         * @param channel whose pipeline should be defined, also to be passed to {@link NetconfSessionNegotiatorFactory}
         * @param promise to be passed to {@link NetconfSessionNegotiatorFactory}
         */
        void initializeChannel(C channel, Promise<S> promise);
    }

    protected interface PipelineInitializer<S extends NetconfSession>
        extends ChannelPipelineInitializer<SocketChannel, S> {

    }


    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfDispatcher.class);

    private final EventLoopGroup bossGroup;

    private final EventLoopGroup workerGroup;

    private final EventExecutor executor;

    protected AbstractNetconfDispatcher(final EventLoopGroup bossGroup, final EventLoopGroup workerGroup) {
        this(GlobalEventExecutor.INSTANCE, bossGroup, workerGroup);
    }

    protected AbstractNetconfDispatcher(final EventExecutor executor, final EventLoopGroup bossGroup,
            final EventLoopGroup workerGroup) {
        this.bossGroup = requireNonNull(bossGroup);
        this.workerGroup = requireNonNull(workerGroup);
        this.executor = requireNonNull(executor);
    }


    /**
     * Creates server. Each server needs factories to pass their instances to client sessions.
     *
     * @param address address to which the server should be bound
     * @param initializer instance of PipelineInitializer used to initialize the channel pipeline
     *
     * @return ChannelFuture representing the binding process
     */
    protected ChannelFuture createServer(final InetSocketAddress address, final PipelineInitializer<S> initializer) {
        return createServer(address, NioServerSocketChannel.class, initializer);
    }

    /**
     * Creates server. Each server needs factories to pass their instances to client sessions.
     *
     * @param address address to which the server should be bound
     * @param channelClass The {@link Class} which is used to create {@link Channel} instances from.
     * @param initializer instance of PipelineInitializer used to initialize the channel pipeline
     *
     * @return ChannelFuture representing the binding process
     */
    protected <C extends Channel> ChannelFuture createServer(final SocketAddress address,
            final Class<? extends ServerChannel> channelClass, final ChannelPipelineInitializer<C, S> initializer) {
        final ServerBootstrap b = new ServerBootstrap();
        b.childHandler(new ChannelInitializer<C>() {

            @Override
            protected void initChannel(final C ch) {
                initializer.initializeChannel(ch, new DefaultPromise<>(executor));
            }
        });

        b.option(ChannelOption.SO_BACKLOG, 128);
        if (LocalServerChannel.class.equals(channelClass) == false) {
            // makes no sense for LocalServer and produces warning
            b.childOption(ChannelOption.SO_KEEPALIVE, true);
            b.childOption(ChannelOption.TCP_NODELAY , true);
        }
        b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        customizeBootstrap(b);

        if (b.group() == null) {
            b.group(bossGroup, workerGroup);
        }
        try {
            b.channel(channelClass);
        } catch (final IllegalStateException e) {
            // FIXME: if this is ok, document why
            LOG.trace("Not overriding channelFactory on bootstrap {}", b, e);
        }

        // Bind and start to accept incoming connections.
        final ChannelFuture f = b.bind(address);
        LOG.debug("Initiated server {} at {}.", f, address);
        return f;
    }

    /**
     * Customize a server bootstrap before the server is created. This allows
     * subclasses to assign non-default server options before the server is
     * created.
     *
     * @param bootstrap Server bootstrap
     */
    protected void customizeBootstrap(final ServerBootstrap bootstrap) {
        // The default is a no-op
    }

    /**
     * Customize a client bootstrap before the connection is attempted. This
     * allows subclasses to assign non-default options before the client is
     * created.
     *
     * @param bootstrap Client bootstrap
     */
    protected void customizeBootstrap(final Bootstrap bootstrap) {
        // The default is a no-op
    }

    /**
     * Creates a client.
     *
     * @param address remote address
     * @param strategy Reconnection strategy to be used when initial connection fails
     *
     * @return Future representing the connection process. Its result represents the combined success of TCP connection
     *         as well as session negotiation.
     */
    protected Future<S> createClient(final InetSocketAddress address, final ReconnectStrategy strategy,
            final PipelineInitializer<S> initializer) {
        final Bootstrap b = new Bootstrap();
        final NetconfSessionPromise<S> p = new NetconfSessionPromise<>(executor, address, strategy, b);
        b.option(ChannelOption.SO_KEEPALIVE, true).handler(
                new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        initializer.initializeChannel(ch, p);
                    }
                });

        customizeBootstrap(b);
        setWorkerGroup(b);
        setChannelFactory(b);

        p.connect();
        LOG.debug("Client created.");
        return p;
    }

    /**
     * Create a client but use a pre-configured bootstrap.
     * This method however replaces the ChannelInitializer in the bootstrap. All other configuration is preserved.
     *
     * @param address remote address
     */
    protected Future<S> createClient(final InetSocketAddress address, final ReconnectStrategy strategy,
            final Bootstrap bootstrap, final PipelineInitializer<S> initializer) {
        final NetconfSessionPromise<S> p = new NetconfSessionPromise<>(executor, address, strategy, bootstrap);

        bootstrap.handler(
                new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        initializer.initializeChannel(ch, p);
                    }
                });

        p.connect();
        LOG.debug("Client created.");
        return p;
    }

    /**
     * Creates a client.
     *
     * @param address remote address
     * @param connectStrategyFactory Factory for creating reconnection strategy to be used when initial connection fails
     * @param reestablishStrategy Reconnection strategy to be used when the already-established session fails
     * @return Future representing the reconnection task. It will report completion based on reestablishStrategy, e.g.
     *         success if it indicates no further attempts should be made and failure if it reports an error
     * @deprecated Use
     *             {@link #createReconnectingClient(InetSocketAddress, ReconnectStrategyFactory, PipelineInitializer)}
     *             instead.
     */
    @Deprecated
    protected Future<Void> createReconnectingClient(final InetSocketAddress address,
            final ReconnectStrategyFactory connectStrategyFactory, final ReconnectStrategy reestablishStrategy,
            final PipelineInitializer<S> initializer) {
        return createReconnectingClient(address, connectStrategyFactory, initializer);
    }

    /**
     * Creates a reconnecting client.
     *
     * @param address remote address
     * @param connectStrategyFactory Factory for creating reconnection strategy for every reconnect attempt
     * @return Future representing the reconnection task. It will report completion based on reestablishStrategy, e.g.
     *         success is never reported, only failure when it runs out of reconnection attempts.
     */
    protected Future<Void> createReconnectingClient(final InetSocketAddress address,
            final ReconnectStrategyFactory connectStrategyFactory, final PipelineInitializer<S> initializer) {
        final Bootstrap b = new Bootstrap();

        final ReconnectPromise<S, L> p = new ReconnectPromise<>(GlobalEventExecutor.INSTANCE, this, address,
                connectStrategyFactory, b, initializer);

        b.option(ChannelOption.SO_KEEPALIVE, true);

        customizeBootstrap(b);
        setWorkerGroup(b);
        setChannelFactory(b);

        p.connect();
        return p;
    }

    private static void setChannelFactory(final Bootstrap bootstrap) {
        // There is no way to detect if this was already set by
        // customizeBootstrap()
        try {
            bootstrap.channel(NioSocketChannel.class);
        } catch (final IllegalStateException e) {
            LOG.trace("Not overriding channelFactory on bootstrap {}", bootstrap, e);
        }
    }

    private void setWorkerGroup(final Bootstrap bootstrap) {
        if (bootstrap.group() == null) {
            bootstrap.group(workerGroup);
        }
    }

    @Deprecated
    @Override
    public void close() {
    }
}
