/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.security.KeyPair;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.session.ClientSessionImpl;

/**
 * Authorization context for incoming call home sessions.
 *
 * @see CallHomeAuthorizationProvider
 */
public abstract class CallHomeAuthorization {
    private static final CallHomeAuthorization REJECTED = new CallHomeAuthorization() {

        @Override
        public boolean isServerAllowed() {
            return false;
        }

        @Override
        protected String getSessionName() {
            return "";
        }

        @Override
        protected void applyTo(ClientSession session) {
            throw new IllegalStateException("Server is not allowed.");
        }
    };

    /**
     * Returns CallHomeAuthorization object with intent to
     * reject incoming connection.
     *
     * <p>
     * {@link CallHomeAuthorizationProvider} may use returned object
     * as return value for
     * {@link CallHomeAuthorizationProvider#provideAuth(java.net.SocketAddress, java.security.PublicKey)}
     * if the incoming session should be rejected due to policy implemented
     * by provider.
     *
     * @return CallHomeAuthorization with {@code isServerAllowed() == false}
     */
    public static final CallHomeAuthorization rejected() {
        return REJECTED;
    }

    /**
     * Creates a builder for CallHomeAuthorization with intent
     * to accept incoming connection and to provide credentials.
     *
     * <p>
     * Note: If session with same sessionName is already opened and
     * active, incoming session will be rejected.
     *
     * @param sessionName Application specific unique identifier for incoming session
     * @param username    Username to be used for authorization
     * @return Builder which allows to specify credentials.
     */
    public static final Builder serverAccepted(String sessionName, String username) {
        return new Builder(sessionName, username);
    }

    /**
     * Returns true if incomming connection is allowed.
     *
     * @return true if incoming connection from SSH Server is allowed.
     */
    public abstract boolean isServerAllowed();

    /**
     * Applies provided authentification to Mina SSH Client Session.
     *
     * @param session Client Session to which authorization parameters will by applied
     */
    protected abstract void applyTo(ClientSession session);

    protected abstract String getSessionName();

    /**
     * Builder for CallHomeAuthorization which accepts incoming connection.
     *
     * <p>
     * Use {@link CallHomeAuthorization#serverAccepted(String, String)} to instantiate
     * builder.
     */
    public static class Builder implements org.opendaylight.yangtools.concepts.Builder<CallHomeAuthorization> {

        private final String nodeId;
        private final String username;
        private final Set<String> passwords = new HashSet<>();
        private final Set<KeyPair> clientKeys = new HashSet<>();

        private Builder(String nodeId, String username) {
            this.nodeId = Preconditions.checkNotNull(nodeId);
            this.username = Preconditions.checkNotNull(username);
        }

        /**
         * Adds password, which will be used for password-based authorization.
         *
         * @param password Password to be used for password-based authorization.
         * @return this builder.
         */
        public Builder addPassword(String password) {
            this.passwords.add(password);
            return this;
        }

        /**
         * Adds public / private key pair to be used for public-key based authorization.
         *
         * @param clientKey Keys to be used for authorization.
         * @return this builder.
         */
        public Builder addClientKeys(KeyPair clientKey) {
            this.clientKeys.add(clientKey);
            return this;
        }

        @Override
        public CallHomeAuthorization build() {
            return new ServerAllowed(nodeId, username, passwords, clientKeys);
        }

    }

    private static class ServerAllowed extends CallHomeAuthorization {

        private final String nodeId;
        private final String username;
        private final Set<String> passwords;
        private final Set<KeyPair> clientKeyPair;

        ServerAllowed(String nodeId, String username, Collection<String> passwords,
                      Collection<KeyPair> clientKeyPairs) {
            this.username = Preconditions.checkNotNull(username);
            this.passwords = ImmutableSet.copyOf(passwords);
            this.clientKeyPair = ImmutableSet.copyOf(clientKeyPairs);
            this.nodeId = Preconditions.checkNotNull(nodeId);
        }

        @Override
        protected String getSessionName() {
            return nodeId;
        }

        @Override
        public boolean isServerAllowed() {
            return true;
        }

        @Override
        protected void applyTo(ClientSession session) {
            Preconditions.checkArgument(session instanceof ClientSessionImpl);
            ((ClientSessionImpl) session).setUsername(username);

            // First try authentication using server host keys, else try password.
            for (KeyPair keyPair : clientKeyPair) {
                session.addPublicKeyIdentity(keyPair);
            }
            for (String password : passwords) {
                session.addPasswordIdentity(password);
            }
        }
    }
}
