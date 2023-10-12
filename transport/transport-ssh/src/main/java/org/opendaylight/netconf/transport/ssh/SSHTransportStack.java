/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;

/**
 * An SSH {@link TransportStack}. Instances of this class are built indirectly.
 */
public abstract sealed class SSHTransportStack extends AbstractOverlayTransportStack<SSHTransportChannel>
        permits SSHClient, SSHServer {
    private final class AuthenticatingUnderlay {
        private final TransportChannel underlayChannel;

        AuthenticatingUnderlay(final TransportChannel underlayChannel) {
            this.underlayChannel = requireNonNull(underlayChannel);
        }

        // auth success
        void onSuccess() {
            addTransportChannel(new SSHTransportChannel(underlayChannel));
        }

        // auth failure, close underlay
        void onFailure() {
            underlayChannel.channel().close();
        }
    }

    /**
     * Session listener responsible for session authentication for both client and server.
     *
     * <P>Triggers authentication flow when after server key is accepted by client,
     * invokes associated handler on authentication success/failure.
     */
    private final class UserAuthSessionListener implements SessionListener {
        @Override
        public void sessionCreated(final Session session) {
            sessions.put(session.getIoSession().getId(), session);
        }

        @Override
        public void sessionException(final Session session, final Throwable throwable) {
            deleteSession(session);
        }

        @Override
        public void sessionDisconnect(final Session session, final int reason, final String msg, final String language,
                final boolean initiator) {
            deleteSession(session);
        }

        @Override
        public void sessionClosed(final Session session) {
            deleteSession(session);
        }

        private void deleteSession(final Session session) {
            final var id = idOf(session);
            sessions.remove(id);
            final var handler = sessionAuthHandlers.remove(id);
            if (handler != null) {
                handler.onFailure();
            }
        }

        @Override
        public void sessionEvent(final Session session, final Event event) {
            if (Event.KeyEstablished == event && session instanceof ClientSession clientSession) {
                // server key is accepted, trigger authentication flow
                try {
                    clientSession.auth().addListener(future -> {
                        if (!future.isSuccess()) {
                            deleteSession(session);
                        }
                    });
                } catch (IOException e) {
                    sessionException(session, e);
                }
            }
            if (Event.Authenticated == event) {
                final var handler = sessionAuthHandlers.remove(idOf(session));
                if (handler != null) {
                    handler.onSuccess();
                }
            }
        }

        private static Long idOf(final Session session) {
            return session.getIoSession().getId();
        }
    }

    private final TransportIoService ioService;

    private final Map<Long, AuthenticatingUnderlay> sessionAuthHandlers = new ConcurrentHashMap<>();
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    SSHTransportStack(final TransportChannelListener listener, final FactoryManager factoryManager,
            final IoHandler handler) {
        super(listener);
        ioService = new TransportIoService(factoryManager, handler);
        factoryManager.addSessionListener(new UserAuthSessionListener());
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        final var channel = underlayChannel.channel();
        final var ioSession = ioService.createSession(channel.localAddress());

        channel.pipeline().addLast(ioSession.getHandler());
        // authentication triggering and handlers processing is performed by UserAuthSessionListener
        sessionAuthHandlers.put(ioSession.getId(), new AuthenticatingUnderlay(underlayChannel));
    }

    @VisibleForTesting
    Collection<Session> getSessions() {
        return sessions.values();
    }
}
