/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;



import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;

import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.future.SshFutureListener;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

class CallHomeSessionContext implements AutoCloseable, CallHomeProtocolSessionContext {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeSessionContext.class);
    static final Session.AttributeKey<CallHomeSessionContext> SESSION_KEY = new Session.AttributeKey<>();

    private final ClientSessionImpl sshSession;
    private final CallHomeAuthorization authorization;
    private final Factory factory;

    private volatile MinaSshNettyChannel nettyChannel = null;
    private final SocketAddress remoteAddress;


    CallHomeSessionContext(ClientSession sshSession, CallHomeAuthorization authorization, SocketAddress remoteAddress,
            Factory factory) {
        this.authorization = Preconditions.checkNotNull(authorization, "authorization");
        Preconditions.checkArgument(this.authorization.isServerAllowed(), "Server was not allowed.");
        Preconditions.checkArgument(sshSession instanceof ClientSessionImpl,
                "sshSession must implement ClientSessionImpl");
        this.factory = factory;
        this.sshSession = (ClientSessionImpl) sshSession;
        this.remoteAddress = sshSession.getIoSession().getRemoteAddress();
        sshSession.setAttribute(SESSION_KEY, this);
    }

    static CallHomeSessionContext getFrom(ClientSession sshSession) {
        return sshSession.getAttribute(SESSION_KEY);
    }

    AuthFuture authorize() throws IOException {
        authorization.applyTo(sshSession);
        return sshSession.auth();
    }

    void openNetconfChannel() {
        LOG.debug("Opening NETCONF Subsystem on {}", sshSession);
        try {
            final ClientChannel netconfChannel = sshSession.createSubsystemChannel("netconf");
            netconfChannel.setStreaming(ClientChannel.Streaming.Async);
            netconfChannel.open().addListener(newSshFutureListener(netconfChannel));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    SshFutureListener<OpenFuture> newSshFutureListener(final ClientChannel netconfChannel) {
        return new SshFutureListener<OpenFuture>() {

            @Override
            public void operationComplete(OpenFuture future) {
                if (future.isOpened()) {
                    netconfChannelOpened(netconfChannel);
                } else {
                    channelOpenFailed(future.getException());
                }

            }
        };
    }

    protected void channelOpenFailed(Throwable e) {
        LOG.error("Unable to open netconf subsystem, disconnecting.", e);
        sshSession.close(false);
    }

    protected void netconfChannelOpened(ClientChannel netconfChannel) {
        nettyChannel = newMinaSshNettyChannel(netconfChannel);
        factory.getChannelOpenListener().onNetconfSubsystemOpened(CallHomeSessionContext.this, new CallHomeChannelActivator() {

            @Override
            public Promise<NetconfClientSession> activate(NetconfClientSessionListener listener) {
                LOG.info("Activating {} channel with {}", remoteAddress, listener);
                Promise<NetconfClientSession> activationPromise = newPromise();
                factory.getNettyGroup().register(nettyChannel).awaitUninterruptibly(500);
                factory.getChannelInitializer(listener).initialize(nettyChannel, activationPromise);
                nettyChannel.pipeline().fireChannelActive();
                return activationPromise;
            }
        });
    }

    protected MinaSshNettyChannel newMinaSshNettyChannel(ClientChannel netconfChannel) {
        return new MinaSshNettyChannel(netconfChannel);
    }

    private Promise<NetconfClientSession> newPromise() {
        return new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
    }

    @Override
    public PublicKey getRemoteServerKey() {
        return sshSession.getKex().getServerKey();
    }

    public String getServerVersion() {
        return sshSession.getServerVersion();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) sshSession.getIoSession().getRemoteAddress();
    }


    @Override
    public void close() throws Exception {
        // FIXME Implement close & cleanup
    }

    static class Factory {

        private final EventLoopGroup nettyGroup;
        private final ReverseSshChannelInitializer.Factory channelFactory;
        private final CallHomeNetconfSubsystemListener subsystemListener;

        public Factory(EventLoopGroup nettyGroup, ReverseSshChannelInitializer.Factory channelFactory,
                CallHomeNetconfSubsystemListener subsystemListener) {
            super();
            this.nettyGroup = Preconditions.checkNotNull(nettyGroup, "nettyGroup");
            this.channelFactory = Preconditions.checkNotNull(channelFactory, "channelFactory");
            this.subsystemListener = Preconditions.checkNotNull(subsystemListener);
        }

        ReverseSshChannelInitializer getChannelInitializer(NetconfClientSessionListener listener) {
            return channelFactory.create(listener);
        }

        CallHomeNetconfSubsystemListener getChannelOpenListener() {
            return this.subsystemListener;
        }

        CallHomeSessionContext create(ClientSession sshSession, CallHomeAuthorization authorization,
                SocketAddress remoteAddress) {
            return new CallHomeSessionContext(sshSession, authorization, remoteAddress, this);
        }

        EventLoopGroup getNettyGroup() {
            return nettyGroup;
        }

    }

}
