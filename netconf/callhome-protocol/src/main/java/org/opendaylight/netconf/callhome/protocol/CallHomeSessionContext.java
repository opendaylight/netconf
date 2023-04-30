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

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.shaded.sshd.client.channel.ClientChannel;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFutureListener;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.Protocol.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Non-final for testing
class CallHomeSessionContext implements CallHomeProtocolSessionContext {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeSessionContext.class);
    private static final String NETCONF = "netconf";

    @VisibleForTesting
    static final Session.AttributeKey<CallHomeSessionContext> SESSION_KEY = new Session.AttributeKey<>();

    private final ClientSession sshSession;
    private final CallHomeAuthorization authorization;
    private final Factory factory;

    private volatile boolean activated;

    private final InetSocketAddress remoteAddress;
    private final PublicKey serverKey;

    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR", justification = "Passing 'this' around")
    CallHomeSessionContext(final ClientSession sshSession, final CallHomeAuthorization authorization,
                           final Factory factory) {
        this.authorization = requireNonNull(authorization, "authorization");
        checkArgument(this.authorization.isServerAllowed(), "Server was not allowed.");
        this.factory = requireNonNull(factory);
        this.sshSession = requireNonNull(sshSession);
        remoteAddress = (InetSocketAddress) this.sshSession.getIoSession().getRemoteAddress();
        serverKey = this.sshSession.getServerKey();
    }

    final void associate() {
        sshSession.setAttribute(SESSION_KEY, this);
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
                factory.getChannelOpenListener().onNetconfSubsystemOpened(this,
                    listener -> doActivate(netconfChannel, listener));
            } else {
                channelOpenFailed(future.getException());
            }
        };
    }

    @Override
    public void terminate() {
        sshSession.close(false);
        removeSelf();
    }

    @Override
    public Name getTransportType() {
        return Name.SSH;
    }

    private void channelOpenFailed(final Throwable throwable) {
        LOG.error("Unable to open netconf subsystem, disconnecting.", throwable);
        sshSession.close(false);
    }

    private synchronized Promise<NetconfClientSession> doActivate(final ClientChannel netconfChannel,
            final NetconfClientSessionListener listener) {
        if (activated) {
            return newSessionPromise().setFailure(new IllegalStateException("Session already activated."));
        }

        activated = true;
        LOG.info("Activating Netconf channel for {} with {}", getRemoteAddress(), listener);
        Promise<NetconfClientSession> activationPromise = newSessionPromise();
        final MinaSshNettyChannel nettyChannel = newMinaSshNettyChannel(netconfChannel);
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
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public String getSessionId() {
        return authorization.getSessionName();
    }

    void removeSelf() {
        factory.remove(this);
    }

    static class Factory {
        private final ConcurrentMap<String, CallHomeSessionContext> sessions = new ConcurrentHashMap<>();
        private final EventLoopGroup nettyGroup;
        private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
        private final CallHomeNetconfSubsystemListener subsystemListener;

        Factory(final EventLoopGroup nettyGroup, final NetconfClientSessionNegotiatorFactory negotiatorFactory,
                final CallHomeNetconfSubsystemListener subsystemListener) {
            this.nettyGroup = requireNonNull(nettyGroup);
            this.negotiatorFactory = requireNonNull(negotiatorFactory);
            this.subsystemListener = requireNonNull(subsystemListener);
        }

        ReverseSshChannelInitializer getChannelInitializer(final NetconfClientSessionListener listener) {
            return ReverseSshChannelInitializer.create(negotiatorFactory, listener);
        }

        CallHomeNetconfSubsystemListener getChannelOpenListener() {
            return subsystemListener;
        }

        EventLoopGroup getNettyGroup() {
            return nettyGroup;
        }

        @Nullable CallHomeSessionContext createIfNotExists(final ClientSession sshSession,
                                                           final CallHomeAuthorization authorization) {
            final var newSession = new CallHomeSessionContext(sshSession, authorization, this);
            final var existing = sessions.putIfAbsent(newSession.getSessionId(), newSession);
            if (existing == null) {
                // There was no mapping, but now there is. Associate the context with the session.
                newSession.associate();
                return newSession;
            }

            // We already have a mapping, do not create a new one. But also check if the current session matches
            // the one stored in the session. This can happen during re-keying.
            return existing == CallHomeSessionContext.getFrom(sshSession) ? existing : null;
        }

        void remove(final CallHomeSessionContext session) {
            sessions.remove(session.getSessionId(), session);
        }
    }
}
