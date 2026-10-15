/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.security.KeyPair;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Credentials an SSH client uses to authenticate to a server.
 *
 * @param username the username
 * @param passwords passwords to try, may be empty if keyPairs is not empty
 * @param keyPairs key pairs to try, may be empty if passwords is not empty
 */
@NonNullByDefault
public record SSHClientCredentials(String username, List<String> passwords, List<KeyPair> keyPairs) {
    /**
     * Default constructor.
     *
     * @throws IllegalArgumentException if both passwords and keyPairs are empty
     */
    public SSHClientCredentials {
        requireNonNull(username);
        passwords = List.copyOf(passwords);
        keyPairs = List.copyOf(keyPairs);
        if (passwords.isEmpty() && keyPairs.isEmpty()) {
            throw new IllegalArgumentException("Neither passwords nor key pairs are defined");
        }
    }

    @Override
    public String toString() {
        // Do not print the passwords and the private keys
        return MoreObjects.toStringHelper(this)
            .add("username", username)
            .add("passwords", passwords.size())
            .add("keyPairs", keyPairs.size())
            .toString();
    }
}
