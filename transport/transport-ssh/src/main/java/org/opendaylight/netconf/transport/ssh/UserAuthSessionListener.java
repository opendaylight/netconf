/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Map;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;

/**
 * Session listener responsible for session authentication for both client and server.
 *
 * <P>Triggers authentication flow when after server key is accepted by client,
 * invokes associated handler on authentication success/failure.
 */
public class UserAuthSessionListener implements SessionListener {

    final Map<Long, AuthHandler> sessionAuthHandlers;
    final Map<Long, Session> sessions;

    public UserAuthSessionListener(final Map<Long, AuthHandler> sessionAuthHandlers,
            final Map<Long, Session> sessions) {
        this.sessionAuthHandlers = sessionAuthHandlers;
        this.sessions = sessions;
    }

    @Override
    public void sessionCreated(Session session) {
        sessions.put(session.getIoSession().getId(), session);
    }

    @Override
    public void sessionException(final Session session, Throwable throwable) {
        deleteSession(session);
    }

    @Override
    public void sessionDisconnect(Session session, int reason, String msg, String language, boolean initiator) {
        deleteSession(session);
    }

    @Override
    public void sessionClosed(Session session) {
        deleteSession(session);
    }

    private void deleteSession(final Session session) {
        final Long id = idOf(session);
        sessions.remove(id);
        final var handler = sessionAuthHandlers.remove(id);
        if (handler != null) {
            handler.onFailure().run();
        }
    }

    @Override
    public void sessionEvent(Session session, Event event) {
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
                handler.onSuccess().run();
            }
        }
    }

    private static Long idOf(final Session session) {
        return session.getIoSession().getId();
    }

    public record AuthHandler(Runnable onSuccess, Runnable onFailure) {
        public AuthHandler {
            requireNonNull(onSuccess);
            requireNonNull(onFailure);
        }
    }
}
