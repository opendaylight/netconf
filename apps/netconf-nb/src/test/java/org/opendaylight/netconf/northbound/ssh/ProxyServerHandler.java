/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.ssh;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServerHandler.class);

    private final Bootstrap clientBootstrap;
    private final LocalAddress localAddress;

    private Channel clientChannel;

    public ProxyServerHandler(final EventLoopGroup bossGroup, final LocalAddress localAddress) {
        clientBootstrap = new Bootstrap();
        clientBootstrap.group(bossGroup).channel(LocalChannel.class);
        this.localAddress = localAddress;
    }

    @Override
    public void channelActive(final ChannelHandlerContext remoteCtx) {
        final ProxyClientHandler clientHandler = new ProxyClientHandler(remoteCtx);
        clientBootstrap.handler(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(final LocalChannel ch) {
                ch.pipeline().addLast(clientHandler);
            }
        });
        ChannelFuture clientChannelFuture = clientBootstrap.connect(localAddress).awaitUninterruptibly();
        clientChannel = clientChannelFuture.channel();
        clientChannel.writeAndFlush(Unpooled.copiedBuffer("connected\n".getBytes()));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        LOG.info("channelInactive - closing client connection");
        clientChannel.close();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        LOG.debug("Writing to client {}", msg);
        clientChannel.write(msg);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        LOG.debug("flushing");
        clientChannel.flush();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        // Close the connection when an exception is raised.
        LOG.warn("Unexpected exception from downstream.", cause);
        ctx.close();
    }
}

