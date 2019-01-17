/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.netty;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler implementation for the echo client.  It initiates the ping-pong
 * traffic between the echo client and server by sending the first message to
 * the server.
 */
public class EchoClientHandler extends ChannelInboundHandlerAdapter implements ChannelFutureListener {
    private static final Logger LOG = LoggerFactory.getLogger(EchoClientHandler.class);

    private ChannelHandlerContext context;
    private final StringBuilder fromServer = new StringBuilder();

    public enum State {
        CONNECTING, CONNECTED, FAILED_TO_CONNECT, CONNECTION_CLOSED
    }


    private State state = State.CONNECTING;

    @Override
    public synchronized void channelActive(ChannelHandlerContext ctx) {
        checkState(context == null);
        LOG.info("channelActive");
        context = ctx;
        state = State.CONNECTED;
    }

    @Override
    public synchronized void channelInactive(ChannelHandlerContext ctx) throws Exception {
        state = State.CONNECTION_CLOSED;
    }

    @Override
    public synchronized void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf bb = (ByteBuf) msg;
        String string = bb.toString(UTF_8);
        fromServer.append(string);
        LOG.info(">{}", string);
        bb.release();
    }

    @Override
    public synchronized void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        LOG.warn("Unexpected exception from downstream.", cause);
        checkState(context.equals(ctx));
        ctx.close();
        context = null;
    }

    public synchronized void write(String message) {
        ByteBuf byteBuf = Unpooled.copiedBuffer(message.getBytes());
        context.writeAndFlush(byteBuf);
    }

    public synchronized boolean isConnected() {
        return state == State.CONNECTED;
    }

    public synchronized String read() {
        return fromServer.toString();
    }

    @Override
    public synchronized void operationComplete(ChannelFuture future) throws Exception {
        checkState(state == State.CONNECTING);
        if (future.isSuccess()) {
            LOG.trace("Successfully connected, state will be switched in channelActive");
        } else {
            state = State.FAILED_TO_CONNECT;
        }
    }

    public State getState() {
        return state;
    }
}
