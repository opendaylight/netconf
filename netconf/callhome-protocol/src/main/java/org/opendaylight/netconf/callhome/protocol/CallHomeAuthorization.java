/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import java.security.KeyPair;

import org.apache.sshd.ClientSession;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

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

    public static final CallHomeAuthorization rejected() {
        return REJECTED;
    }

    public static final Builder serverAccepted(String username) {
        return new Builder(username);
    }

    public abstract boolean isServerAllowed();

    protected abstract void applyTo(ClientSession session);


    public static class Builder implements org.opendaylight.yangtools.concepts.Builder<CallHomeAuthorization> {

        private final String username;
        private String password ;
        private KeyPair clientKey ;

        private Builder(String username) {
            this.username = Preconditions.checkNotNull(username);
        }

        public Builder addPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder addClientKeys(KeyPair clientKey){
            this.clientKey = clientKey;
            return this;
        }

        @Override
        public CallHomeAuthorization build() {
            try {
                return new ServerAllowed(username, password, clientKey);
            }catch(Exception ex){
                ex.printStackTrace();
            }
            return null;
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
