/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;


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
import java.net.SocketAddress;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandlerReader;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandlerReader.ReadMsgHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandlerWriter;

public class MinaSshNettyChannel extends AbstractServerChannel {


    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final ClientSession session;
    private final ClientChannel sshChannel;

    private final AsyncSshHandlerReader  sshReadHandler;
    private final AsyncSshHandlerWriter sshWriteAsyncHandler;



    public MinaSshNettyChannel(ClientSession session, ClientChannel sshChannel) {
        this.session = Preconditions.checkNotNull(session);
        this.sshChannel = Preconditions.checkNotNull(sshChannel);
        this.sshReadHandler = new AsyncSshHandlerReader(new ConnectionClosed(), new FireMessage(), "netconf",
                sshChannel.getAsyncOut());
        this.sshWriteAsyncHandler = new AsyncSshHandlerWriter(sshChannel.getAsyncIn());
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

    private boolean notClosing(org.apache.sshd.common.Closeable sshCloseable) {
        return !sshCloseable.isClosing() && !sshCloseable.isClosed();
    }


    @Override
    public boolean isOpen() {
        return notClosing(session);
    }

    @Override
    public boolean isActive() {
        return notClosing(session);
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
        pipeline().fireChannelInactive();
        sshReadHandler.close();
        sshWriteAsyncHandler.close();
        sshChannel.close(false);
        session.close(false);
    }

    @Override
    protected void doClose() throws Exception {
        if(notClosing(session)) {
            sshChannel.close(true);
            sshChannel.close(true);
        }
    }

    @Override
    protected void doBeginRead() throws Exception {
        // Intentional NOOP - read is started by AsyncSshHandlerReader
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception
    {
        throw new IllegalStateException("Outbound writes to SSH should be done by SSH Write handler");
    }

    private final class FireMessage implements ReadMsgHandler {

        @Override
        public void onMessageRead(ByteBuf msg) {
            pipeline().fireChannelRead(msg);
        }

    }

    private final class ConnectionClosed implements AutoCloseable {

        /**
         * Invoked when SSH session dropped during read using {@link AsyncSshHandlerReader}
         */
        @Override
        public void close() throws Exception {
            // Noop
        }

    }

    private class SshUnsafe extends AbstractUnsafe {

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            throw new UnsupportedOperationException("Unsafe is not supported.");
        }

    }
}
