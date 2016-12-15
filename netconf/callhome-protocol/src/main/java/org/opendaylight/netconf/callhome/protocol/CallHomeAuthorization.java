/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import com.google.common.base.Preconditions;
import java.security.KeyPair;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Authorization context for incomming call home sessions.
 *
 * @see CallHomeAuthorizationProvider
 */
public abstract class CallHomeAuthorization {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeAuthorization.class);
    private static final CallHomeAuthorization REJECTED = new CallHomeAuthorization() {

        @Override
        public boolean isServerAllowed() {
            return false;
        }

        @Override
        protected void applyTo(ClientSession session) {
            throw new IllegalStateException("Server is not allowed.");
        }
    };

    /**
     *
     * Returns CallHomeAuthorization object with intent to
     * reject incoming connection.
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
     * @param username Username to be used for authorization
     * @return Builder which allows to specify credentials.
     */
    public static final Builder serverAccepted(String username) {
        return new Builder(username);
    }

    /**
     * Returns true if incomming connection is allowed.
     *
     * @return true if incoming connection from SSH Server is allowed.
     */
    public abstract boolean isServerAllowed();

    /**
     *
     * Applies provided authentification to Mina SSH Client Session
     *
     * @param session
     */
    protected abstract void applyTo(ClientSession session);


    /**
     *
     * Builder for CallHomeAuthorization which accepts incoming connection.
     *
     * Use {@link CallHomeAuthorization#serverAccepted(String)} to instantiate
     * builder.
     *
     */
    public static class Builder implements org.opendaylight.yangtools.concepts.Builder<CallHomeAuthorization> {

        private final String username;
        private String password ;
        private KeyPair clientKey ;

        private Builder(String username) {
            this.username = Preconditions.checkNotNull(username);
        }

        /**
         *
         * Adds password, which will be used for password-based authorization.
         *
         * @param password Password to be used for password-based authorization.
         * @return this builder.
         */
        public Builder addPassword(String password) {
            this.password = password;
            return this;
        }

        /**
         *
         * Adds public / private key pair to be used for public-key based authorization.
         *
         * @param clientKey Keys to be used for authorization.
         * @return this builder.
         */
        public Builder addClientKeys(KeyPair clientKey){
            this.clientKey = clientKey;
            return this;
        }

        @Override
        public CallHomeAuthorization build() {
            return new ServerAllowed(username, password, clientKey);
        }
    }

    private static class ServerAllowed extends CallHomeAuthorization {

        private final String username;
        private final String password;
        private final KeyPair clientKeyPair;

        ServerAllowed(String username, String password, KeyPair clientKeyPair) {
            this.username = Preconditions.checkNotNull(username);
            this.password = password;
            this.clientKeyPair = clientKeyPair;
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
            if(clientKeyPair!=null)
                session.addPublicKeyIdentity(clientKeyPair);

            if(password!=null)
                session.addPasswordIdentity(password);
        }
    }
}
