/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.tcp.netty;

import static com.google.common.base.Preconditions.checkState;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyClientHandler.class);

    private final ChannelHandlerContext remoteCtx;
    private ChannelHandlerContext localCtx;

    ProxyClientHandler(ChannelHandlerContext remoteCtx) {
        this.remoteCtx = remoteCtx;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        checkState(this.localCtx == null);
        LOG.trace("Client channel active");
        this.localCtx = ctx;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        LOG.trace("Forwarding message");
        remoteCtx.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        LOG.trace("Flushing remote ctx");
        remoteCtx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        LOG.warn("Unexpected exception from downstream", cause);
        checkState(this.localCtx.equals(ctx));
        ctx.close();
    }

    // called both when local or remote connection dies
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOG.trace("channelInactive() called, closing remote client ctx");
        remoteCtx.close();
    }

}
