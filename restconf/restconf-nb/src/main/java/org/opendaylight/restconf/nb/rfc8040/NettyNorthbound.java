/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.annotations.Beta;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.opendaylight.restconf.nb.netty.JsonToMessageEncoder;
import org.opendaylight.restconf.nb.netty.MessageToJsonDecoder;
import org.opendaylight.restconf.nb.netty.NettyRestconf;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Beta
@Component(service = { })
public final class NettyNorthbound implements AutoCloseable {
    private static final int port = 18181;

    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;
    private final ChannelFuture channelFuture;

    @Activate
    public NettyNorthbound(@Reference final RestconfServer server) throws Exception {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        final var bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new NettyRestconf(server),
                        new JsonToMessageEncoder(), new MessageToJsonDecoder());
                }
            })
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true);
        channelFuture = bootstrap.bind(port);
    }

    @Deactivate
    @Override
    public void close() throws Exception {
        channelFuture.channel().closeFuture();
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }
}
