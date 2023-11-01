/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.ssh;

import static java.util.Objects.requireNonNull;

import java.security.KeyPair;
import java.util.Collection;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

/**
 * Authorization context for incoming call home sessions.
 *
 * @see CallHomeSshAuthProvider
 */
public interface CallHomeSshAuthSettings {

    /**
     * Unique identifier of a client this auth settings belongs to.
     *
     * @return identifier
     */
    String id();

    /**
     * Applies auth settings on {@link ClientSession} instance for subsequent {@link ClientSession#auth()} invocation.
     *
     * @param session client session object
     */
    void applyTo(ClientSession session);

    /**
     * Default implementation of {@link CallHomeSshAuthSettings}. Serves SSH authentication by password(s) and/or
     * public key(s).
     *
     * @param id unique client identifier
     * @param username username
     * @param passwords collection of passwords, optional if keyPairs defined
     * @param keyPairs collection of {@link KeyPair} objects, optional if passwords defined
     */
    record DefaultAuthSettings(@NonNull String id, @NonNull  String username, @Nullable Collection<String> passwords,
            @Nullable Collection<KeyPair> keyPairs) implements CallHomeSshAuthSettings {

        public DefaultAuthSettings {
            requireNonNull(id);
            requireNonNull(username);
            if ((passwords == null || passwords.isEmpty()) && (keyPairs == null || keyPairs.isEmpty())) {
                throw new IllegalArgumentException("Neither passwords nor keyPairs is defined");
            }
        }

        @Override
        public void applyTo(final ClientSession session) {
            session.setUsername(username);
            if (keyPairs != null) {
                for (KeyPair keyPair : keyPairs) {
                    session.addPublicKeyIdentity(keyPair);
                }
            }
            if (passwords != null) {
                for (String password : passwords) {
                    session.addPasswordIdentity(password);
                }
            }
        }
    }
}
