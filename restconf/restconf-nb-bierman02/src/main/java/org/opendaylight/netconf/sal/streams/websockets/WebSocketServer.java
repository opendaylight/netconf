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
public class WebSocketServer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketServer.class);

    private static WebSocketServer instance = null;

    private final int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;


    private WebSocketServer(final int port) {
        this.port = port;
    }

    /**
     * Create singleton instance of {@link WebSocketServer}.
     *
     * @param port TCP port used for this server
     * @return instance of {@link WebSocketServer}
     */
    public static WebSocketServer createInstance(final int port) {
        Preconditions.checkState(instance == null, "createInstance() has already been called");
        Preconditions.checkArgument(port >= 1024, "Privileged port (below 1024) is not allowed");

        instance = new WebSocketServer(port);
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

            final Channel channel = serverBootstrap.bind(port).sync().channel();
            LOG.info("Web socket server started at port {}.", port);

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
        LOG.debug("Stopping the web socket server instance on port {}", port);
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
