/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static java.util.Objects.requireNonNull;

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import java.net.SocketAddress;
import org.opendaylight.netconf.callhome.protocol.CallHomeSessionContext.SshWriteAsyncHandlerAdapter;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Non-final for testing
class MinaSshNettyChannel extends AbstractServerChannel {
    private static final Logger LOG = LoggerFactory.getLogger(MinaSshNettyChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final CallHomeSessionContext context;
    private final ClientSession session;

    private volatile boolean nettyClosed = false;

    MinaSshNettyChannel(final CallHomeSessionContext context, final ClientSession session) {
        this.context = requireNonNull(context);
        this.session = requireNonNull(session);
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    private static boolean notClosing(final org.opendaylight.netconf.shaded.sshd.common.Closeable sshCloseable) {
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
    protected boolean isCompatible(final EventLoop loop) {
        return true;
    }

    @Override
    protected SocketAddress localAddress0() {
        return session.getIoSession().getLocalAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return context.getRemoteAddress();
    }

    @Override
    protected void doBind(final SocketAddress localAddress) {
        throw new UnsupportedOperationException("Bind not supported.");
    }

    void doMinaDisconnect(final boolean blocking) {
        if (notClosing(session)) {
            session.close(blocking);
            if (pipeline().first() instanceof SshWriteAsyncHandlerAdapter asyncHandlerAdapter) {
                asyncHandlerAdapter.getSshChannel().close(blocking);
            }
        }
    }

    void doNettyDisconnect() {
        if (!nettyClosed) {
            nettyClosed = true;
            pipeline().fireChannelInactive();
        }
    }

    @Override
    protected void doDisconnect() {
        LOG.info("Disconnect invoked");
        doNettyDisconnect();
        doMinaDisconnect(false);
    }

    @Override
    protected void doClose() {
        context.removeSelf();
        if (notClosing(session)) {
            session.close(true);
            if (pipeline().first() instanceof SshWriteAsyncHandlerAdapter asyncHandlerAdapter) {
                asyncHandlerAdapter.getSshChannel().close(true);
            }
        }
    }

    @Override
    protected void doBeginRead() {
        // Intentional NOOP
    }

    @Override
    protected void doWrite(final ChannelOutboundBuffer in) {
        throw new IllegalStateException("Outbound writes to SSH should be done by SSH Write handler");
    }

    private class SshUnsafe extends AbstractUnsafe {
        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress,
                final ChannelPromise promise) {
            throw new UnsupportedOperationException("Unsafe is not supported.");
        }
    }
}
