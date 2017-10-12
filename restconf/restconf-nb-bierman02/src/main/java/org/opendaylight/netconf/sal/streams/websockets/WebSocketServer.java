/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.streams.websockets;

import com.google.common.base.Preconditions;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link WebSocketServer} is the singleton responsible for starting and stopping the
 * web socket server.
 */
public final class WebSocketServer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketServer.class);

    private static final String DEFAULT_ADDRESS = "0.0.0.0";

    private static WebSocketServer instance = null;

    private final String address;
    private final int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;


    private WebSocketServer(final String address, final int port) {
        this.address = address;
        this.port = port;
    }

    /**
     * Create singleton instance of {@link WebSocketServer}.
     *
     * @param port TCP port used for this server
     * @return instance of {@link WebSocketServer}
     */
    private static WebSocketServer createInstance(final int port) {
        instance = createInstance(DEFAULT_ADDRESS, port);
        return instance;
    }

    public static WebSocketServer createInstance(final String address, final int port) {
        Preconditions.checkState(instance == null, "createInstance() has already been called");
        Preconditions.checkNotNull(address, "Address cannot be null.");
        Preconditions.checkArgument(port >= 1024, "Privileged port (below 1024) is not allowed");

        instance = new WebSocketServer(address, port);
        return instance;
    }

    /**
     * Get the websocket of TCP port.
     *
     * @return websocket TCP port
     */
    public int getPort() {
        return port;
    }

    /**
     * Get instance of {@link WebSocketServer} created by {@link #createInstance(int)}.
     *
     * @return instance of {@link WebSocketServer}
     */
    public static WebSocketServer getInstance() {
        Preconditions.checkNotNull(instance, "createInstance() must be called prior to getInstance()");
        return instance;
    }

    /**
     * Get instance of {@link WebSocketServer} created by {@link #createInstance(int)}.
     * If an instance doesnt exist create one with the provided fallback port.
     *
     * @return instance of {@link WebSocketServer}
     */
    public static WebSocketServer getInstance(final int fallbackPort) {
        if (instance != null) {
            return instance;
        }

        LOG.warn("No instance for WebSocketServer found, creating one with a fallback port: {}", fallbackPort);
        return createInstance(fallbackPort);
    }

    /**
     * Destroy the existing instance.
     */
    public static void destroyInstance() {
        Preconditions.checkState(instance != null, "createInstance() must be called prior to destroyInstance()");

        instance.stop();
        instance = null;
    }

    @Override
    public void run() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        try {
            final ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new WebSocketServerInitializer());

            final Channel channel = serverBootstrap.bind(address, port).sync().channel();
            LOG.info("Web socket server started at address {}, port {}.", address, port);

            channel.closeFuture().sync();
        } catch (final InterruptedException e) {
            LOG.error("Web socket server encountered an error during startup attempt on port {}", port, e);
        } finally {
            stop();
        }
    }

    /**
     * Stops the web socket server and removes all listeners.
     */
    private void stop() {
        LOG.info("Stopping the web socket server instance on port {}", port);
        Notificator.removeAllListeners();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }

}
