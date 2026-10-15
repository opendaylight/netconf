/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import static java.util.Objects.requireNonNull;

import java.security.KeyPair;
import java.util.Collection;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.ssh.SSHClientCredentials;

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
     * Returns the credentials used to authenticate the call home session.
     *
     * @return the credentials
     */
    SSHClientCredentials credentials();

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
        public SSHClientCredentials credentials() {
            return new SSHClientCredentials(username, passwords == null ? List.of() : List.copyOf(passwords),
                keyPairs == null ? List.of() : List.copyOf(keyPairs));
        }
    }
}
