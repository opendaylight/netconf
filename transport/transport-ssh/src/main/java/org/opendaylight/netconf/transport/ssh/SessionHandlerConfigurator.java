/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import java.security.PublicKey;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.AttributeRepository.AttributeKey;
import org.opendaylight.netconf.shaded.sshd.common.config.keys.KeyUtils;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;

/**
 * A {@link ClientFactoryManagerConfigurator} which delegates the server key check, the client credentials and the
 * session closure to an {@link SSHClientSessionHandler}.
 */
@NonNullByDefault
final class SessionHandlerConfigurator extends ClientFactoryManagerConfigurator {
    // The server key the handler accepted for a session
    private static final AttributeKey<PublicKey> ACCEPTED_SERVER_KEY = new AttributeKey<>();

    private final SSHClientSessionHandler handler;

    SessionHandlerConfigurator(final SSHClientSessionHandler handler) {
        this.handler = requireNonNull(handler);
    }

    @Override
    protected void configureClientFactoryManager(final ClientFactoryManager factoryManager) {
        factoryManager.setServerKeyVerifier(
            (clientSession, remoteAddress, serverKey) -> verifyServerKey(clientSession, serverKey));
        factoryManager.addSessionListener(new SessionListener() {
            // Called for every closed client session, also for sessions whose server key was rejected
            @Override
            public void sessionClosed(final Session session) {
                if (session instanceof ClientSession clientSession) {
                    handler.sessionClosed(new ClientSessionInfo(clientSession));
                }
            }
        });
        // Password and public key are the methods SSHClientCredentials can provide
        factoryManager.setUserAuthFactories(List.of(UserAuthPasswordFactory.INSTANCE, new UserAuthPublicKeyFactory()));
    }

    /**
     * Called by sshd at the end of every key exchange. For the first one, before authentication, the handler decides
     * whether the server is accepted. If it is, its credentials are set on the session, and sshd uses them when
     * {@link SSHClient} starts authentication.
     *
     * <p>sshd repeats the key exchange on an established session, by default every hour. The handler is not asked
     * again then. The server is accepted only if it still presents the key the handler accepted.
     *
     * @param clientSession the session
     * @param serverKey the public key of the server
     * @return {@code true} if the server is accepted, {@code false} to reject it
     */
    private boolean verifyServerKey(final ClientSession clientSession, final PublicKey serverKey) {
        final var acceptedKey = clientSession.getAttribute(ACCEPTED_SERVER_KEY);
        if (acceptedKey != null) {
            return KeyUtils.compareKeys(acceptedKey, serverKey);
        }

        final var credentials = handler.checkServerKey(new ClientSessionInfo(clientSession), serverKey,
            NegotiatedAlgorithms.of(clientSession));
        if (credentials == null) {
            return false;
        }
        clientSession.setUsername(credentials.username());
        credentials.keyPairs().forEach(clientSession::addPublicKeyIdentity);
        credentials.passwords().forEach(clientSession::addPasswordIdentity);
        clientSession.setAttribute(ACCEPTED_SERVER_KEY, serverKey);
        return true;
    }
}
