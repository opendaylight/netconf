/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;


import java.net.SocketAddress;

import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandlerReader;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandlerReader.ReadMsgHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandlerWriter;

import com.google.common.base.Preconditions;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;

// FIXME: Should be named MinaSshNettyChannelAdapter?
public class MinaSshNettyChannel extends AbstractServerChannel {


    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ChannelConfig config = new DefaultChannelConfig(this);
    ClientSession session;
    ClientChannel sshChannel;

    private AsyncSshHandlerReader sshReadHandler;
    private AsyncSshHandlerWriter sshWriteAsyncHandler;



    public MinaSshNettyChannel(ClientChannel sshChannel) {
        super();
        this.sshChannel = Preconditions.checkNotNull(sshChannel);
        sshReadHandler = new AsyncSshHandlerReader(new ConnectionClosed(), new FireMessage(), "netconf", sshChannel.getAsyncOut());
        sshWriteAsyncHandler = new AsyncSshHandlerWriter(sshChannel.getAsyncIn());
        pipeline().addFirst(createChannelAdapter());
    }

    private ChannelOutboundHandlerAdapter createChannelAdapter()
    {
        return new ChannelOutboundHandlerAdapter() {

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                sshWriteAsyncHandler.write(ctx, msg, promise);
            }

        };
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
       return new SshUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    @Override
    protected SocketAddress localAddress0() {
        return session.getIoSession().getLocalAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return session.getIoSession().getRemoteAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException("Bind not supported.");

    }

    @Override
    protected void doDisconnect() throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    protected void doClose() throws Exception {
        // FIXME: implement this.
    }

    @Override
    protected void doBeginRead() throws Exception {

    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception
    {
        throw new UnsupportedOperationException("Outbound writes to SSH should be done by SSH Write handler");
    }

    private final class FireMessage implements ReadMsgHandler {

        @Override
        public void onMessageRead(ByteBuf msg) {
            pipeline().fireChannelRead(msg);
        }

    }

    private final class ConnectionClosed implements AutoCloseable {

        @Override
        public void close() throws Exception {
            // TODO Auto-generated method stub

        }

    }

    private class SshUnsafe extends AbstractUnsafe {

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            throw new UnsupportedOperationException("Unsafe is not supported.");
        }

    }
}
