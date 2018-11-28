/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.tcp.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class);

    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ChannelFuture channelFuture;

    @SuppressWarnings("checkstyle:IllegalCatch")
    public ProxyServer(InetSocketAddress address, final LocalAddress localAddress) {
        // Configure the server.
        final Bootstrap clientBootstrap = new Bootstrap();
        clientBootstrap.group(bossGroup).channel(LocalChannel.class);

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ProxyServerHandler(clientBootstrap, localAddress));
                    }
                });

        // Start the server.
        try {
            channelFuture = serverBootstrap.bind(address).syncUninterruptibly();
        } catch (Throwable throwable) {
            // sync() re-throws exceptions declared as Throwable, so the compiler doesn't see them
            LOG.error("Error while binding to address {}", address, throwable);
            throw throwable;
        }
    }

    @Override
    public void close() {
        channelFuture.channel().close();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
