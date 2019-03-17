/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import com.google.common.base.Preconditions;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.opendaylight.restconf.common.configuration.RestconfConfigurationHolder;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link WebSocketServer} is the class that is responsible for starting and stopping of web-socket server with
 * specified listening TCP port and security type.
 */
@SuppressWarnings("unused")
public final class WebSocketServer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketServer.class);

    private final Integer port;
    private final RestconfConfigurationHolder.SecurityType securityType;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * Creates instance of web-socket server using defined port and security features.
     *
     * @param port         TCP port used for this server.
     * @param securityType Security type that should be used for protecting of web-socket server and its communication.
     */
    public WebSocketServer(final Integer port, final RestconfConfigurationHolder.SecurityType securityType) {
        this.port = Preconditions.checkNotNull(port);
        this.securityType = Preconditions.checkNotNull(securityType);
    }

    /**
     * Get the TCP port of websocket server.
     *
     * @return TCP port number.
     */
    public Integer getPort() {
        return port;
    }

    /**
     * Get the security type that is applied to web-socket server.
     *
     * @return Security type that web-socket server provides.
     */
    public RestconfConfigurationHolder.SecurityType getSecurityType() {
        return securityType;
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void run() {
        try {
            final Channel channel = createWebSocketChannel();
            channel.closeFuture().sync();
        } catch (final InterruptedException exception) {
            LOG.error("Web socket server encountered an error during startup attempt on port {}.", port, exception);
        } catch (final Throwable throwable) {
            // sync() re-throws exceptions declared as Throwable, so the compiler doesn't see them
            LOG.error("Error while binding to port {}.", port, throwable);
            throw throwable;
        } finally {
            stop();
        }
    }

    /**
     * Creation of the web-socket channel based on configured security type.
     *
     * @return Created web-socket channel.
     * @throws InterruptedException Unexpected interruption during creation of web-socket channel.
     */
    private synchronized Channel createWebSocketChannel() throws InterruptedException {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        Channel channel;
        switch (securityType) {
            case DISABLED:
                channel = configureNonSecuredServerChannel();
                break;
            default:
                LOG.warn("Security type {} of web-socket server is currently not supported, "
                        + "starting of non-secured web-socket server.", securityType);
                channel = configureNonSecuredServerChannel();
        }
        return channel;
    }

    private Channel configureNonSecuredServerChannel() throws InterruptedException {
        final ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                .childHandler(new WebSocketServerInitializer());
        final Channel channel = serverBootstrap.bind(port).sync().channel();
        LOG.info("Web socket server started at port {}.", port);
        return channel;
    }

    /**
     * Stops the web socket server and removes all listeners.
     */
    public synchronized void stop() {
        LOG.debug("Stopping the web socket server instance on port {}.", port);
        ListenersBroker.getInstance().removeAndCloseAllListeners();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }

    @Override
    public String toString() {
        return "WebSocketServer{"
                + "port=" + port
                + ", securityType=" + securityType
                + '}';
    }
}