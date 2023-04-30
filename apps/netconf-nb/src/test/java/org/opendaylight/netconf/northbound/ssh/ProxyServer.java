/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.ssh;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.net.InetSocketAddress;

public class ProxyServer implements Runnable {
    private final ProxyHandlerFactory proxyHandlerFactory;

    public ProxyServer(final ProxyHandlerFactory proxyHandlerFactory) {
        this.proxyHandlerFactory = proxyHandlerFactory;
    }

    @Override
    public void run() {
        // Configure the server.
        final EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            final LocalAddress localAddress = new LocalAddress("netconf");
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(final SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(proxyHandlerFactory.create(bossGroup, localAddress));
                        }
                    });

            // Start the server.
            InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
            ChannelFuture future = serverBootstrap.bind(address).sync();

            // Wait until the server socket is closed.
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public interface ProxyHandlerFactory {
        ChannelHandler create(EventLoopGroup bossGroup, LocalAddress localAddress);
    }

    public static void main(final String[] args) {
        ProxyHandlerFactory proxyHandlerFactory = ProxyServerHandler::new;
        start(proxyHandlerFactory);
    }

    public static void start(final ProxyHandlerFactory proxyHandlerFactory) {
        new Thread(new EchoServer()).start();
        new Thread(new ProxyServer(proxyHandlerFactory)).start();
    }
}
