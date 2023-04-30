/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.ssh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyClientHandler.class);

    private final ChannelHandlerContext remoteCtx;


    ProxyClientHandler(final ChannelHandlerContext remoteCtx) {
        this.remoteCtx = remoteCtx;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        LOG.info("client active");
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        ByteBuf bb = (ByteBuf) msg;
        LOG.info(">{}", bb.toString(StandardCharsets.UTF_8));
        remoteCtx.write(msg);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        LOG.debug("Flushing server ctx");
        remoteCtx.flush();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        // Close the connection when an exception is raised.
        LOG.warn("Unexpected exception from downstream", cause);
        ctx.close();
    }

    // called both when local or remote connection dies
    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        LOG.debug("channelInactive() called, closing remote client ctx");
        remoteCtx.close();
    }
}
