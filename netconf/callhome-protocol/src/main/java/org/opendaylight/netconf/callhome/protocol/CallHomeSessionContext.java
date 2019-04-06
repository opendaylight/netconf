/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.session.Session;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CallHomeSessionContext implements CallHomeProtocolSessionContext {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeSessionContext.class);
    static final Session.AttributeKey<CallHomeSessionContext> SESSION_KEY = new Session.AttributeKey<>();

    private static final String NETCONF = "netconf";

    private final ClientSessionImpl sshSession;
    private final CallHomeAuthorization authorization;
    private final Factory factory;

    private volatile MinaSshNettyChannel nettyChannel = null;
    private volatile boolean activated;

    private final InetSocketAddress remoteAddress;
    private final PublicKey serverKey;

    CallHomeSessionContext(final ClientSession sshSession, final CallHomeAuthorization authorization,
                           final SocketAddress remoteAddress, final Factory factory) {
        this.authorization = requireNonNull(authorization, "authorization");
        checkArgument(this.authorization.isServerAllowed(), "Server was not allowed.");
        checkArgument(sshSession instanceof ClientSessionImpl,
                "sshSession must implement ClientSessionImpl");
        this.factory = requireNonNull(factory, "factory");
        this.sshSession = (ClientSessionImpl) sshSession;
        this.sshSession.setAttribute(SESSION_KEY, this);
        this.remoteAddress = (InetSocketAddress) this.sshSession.getIoSession().getRemoteAddress();
        this.serverKey = this.sshSession.getKex().getServerKey();
    }

    static CallHomeSessionContext getFrom(final ClientSession sshSession) {
        return sshSession.getAttribute(SESSION_KEY);
    }

    AuthFuture authorize() throws IOException {
        authorization.applyTo(sshSession);
        return sshSession.auth();
    }

    void openNetconfChannel() {
        LOG.debug("Opening NETCONF Subsystem on {}", sshSession);
        try {
            final ClientChannel netconfChannel = sshSession.createSubsystemChannel(NETCONF);
            netconfChannel.setStreaming(ClientChannel.Streaming.Async);
            netconfChannel.open().addListener(newSshFutureListener(netconfChannel));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    SshFutureListener<OpenFuture> newSshFutureListener(final ClientChannel netconfChannel) {
        return future -> {
            if (future.isOpened()) {
                netconfChannelOpened(netconfChannel);
            } else {
                channelOpenFailed(future.getException());
            }
        };
    }

    private void channelOpenFailed(final Throwable throwable) {
        LOG.error("Unable to open netconf subsystem, disconnecting.", throwable);
        sshSession.close(false);
    }

    private void netconfChannelOpened(final ClientChannel netconfChannel) {
        nettyChannel = newMinaSshNettyChannel(netconfChannel);
        factory.getChannelOpenListener().onNetconfSubsystemOpened(
            CallHomeSessionContext.this, this::doActivate);
    }

    // FIXME: this does not look right
    @Holding("this")
    private synchronized Promise<NetconfClientSession> doActivate(final NetconfClientSessionListener listener) {
        if (activated) {
            return newSessionPromise().setFailure(new IllegalStateException("Session already activated."));
        }
        activated = true;
        LOG.info("Activating Netconf channel for {} with {}", getRemoteAddress(), listener);
        Promise<NetconfClientSession> activationPromise = newSessionPromise();
        factory.getChannelInitializer(listener).initialize(nettyChannel, activationPromise);
        factory.getNettyGroup().register(nettyChannel).awaitUninterruptibly(500);
        return activationPromise;
    }

    protected MinaSshNettyChannel newMinaSshNettyChannel(final ClientChannel netconfChannel) {
        return new MinaSshNettyChannel(this, sshSession, netconfChannel);
    }

    private static Promise<NetconfClientSession> newSessionPromise() {
        return GlobalEventExecutor.INSTANCE.newPromise();
    }

    @Override
    public PublicKey getRemoteServerKey() {
        return serverKey;
    }

    @Override
    public String getRemoteServerVersion() {
        return sshSession.getServerVersion();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public String getSessionName() {
        return authorization.getSessionName();
    }

    void removeSelf() {
        factory.remove(this);
    }

    static class Factory {

        private final EventLoopGroup nettyGroup;
        private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
        private final CallHomeNetconfSubsystemListener subsystemListener;
        private final ConcurrentMap<String, CallHomeSessionContext> sessions = new ConcurrentHashMap<>();

        Factory(final EventLoopGroup nettyGroup, final NetconfClientSessionNegotiatorFactory negotiatorFactory,
                final CallHomeNetconfSubsystemListener subsystemListener) {
            this.nettyGroup = requireNonNull(nettyGroup, "nettyGroup");
            this.negotiatorFactory = requireNonNull(negotiatorFactory, "negotiatorFactory");
            this.subsystemListener = requireNonNull(subsystemListener);
        }

        void remove(final CallHomeSessionContext session) {
            sessions.remove(session.getSessionName(), session);
        }

        ReverseSshChannelInitializer getChannelInitializer(final NetconfClientSessionListener listener) {
            return ReverseSshChannelInitializer.create(negotiatorFactory, listener);
        }

        CallHomeNetconfSubsystemListener getChannelOpenListener() {
            return this.subsystemListener;
        }

        @Nullable CallHomeSessionContext createIfNotExists(final ClientSession sshSession,
                final CallHomeAuthorization authorization, final SocketAddress remoteAddress) {
            CallHomeSessionContext session = new CallHomeSessionContext(sshSession, authorization,
                    remoteAddress, this);
            CallHomeSessionContext preexisting = sessions.putIfAbsent(session.getSessionName(), session);
            // If preexisting is null - session does not exist, so we can safely create new one, otherwise we return
            // null and incoming connection will be rejected.
            return preexisting == null ? session : null;
        }

        EventLoopGroup getNettyGroup() {
            return nettyGroup;
        }
    }
}
