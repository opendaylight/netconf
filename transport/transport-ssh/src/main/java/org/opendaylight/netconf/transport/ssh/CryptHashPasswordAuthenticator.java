/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.Crypt;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.PasswordAuthenticator;
import org.opendaylight.netconf.shaded.sshd.server.session.ServerSession;

final class CryptHashPasswordAuthenticator implements PasswordAuthenticator {
    private record CryptHash(String salt, String hash) {
        CryptHash {
            requireNonNull(salt);
            requireNonNull(hash);
        }
    }

    private static final Pattern CRYPT_HASH_PATTERN = Pattern.compile(
        // clear text, or
        "\\$0\\$.*"
        // MD5, or
        + "|\\$1\\$[a-zA-Z0-9./]{1,8}\\$[a-zA-Z0-9./]{22}"
        // SHA-256, or
        + "|\\$5\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{43}"
        // SHA-512
        + "|\\$6\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{86}");
    private static final String DEFAULT_SALT = "$5$rounds=3500$default";

    private final ImmutableMap<String, CryptHash> knownHashes;

    CryptHashPasswordAuthenticator(final Map<String, String> userHashes) {
        if (userHashes.isEmpty()) {
            throw new IllegalArgumentException("Hashes map should not be empty");
        }

        final var mapBuilder = ImmutableMap.<String, CryptHash>builder();
        for (var entry : userHashes.entrySet()) {
            final var hash = entry.getValue();
            if (!CRYPT_HASH_PATTERN.matcher(hash).matches()) {
                throw new IllegalArgumentException("Invalid crypt hash string \"" + hash + '"');
            }

            mapBuilder.put(entry.getKey(), hash.startsWith("$0$")
                ? new CryptHash(DEFAULT_SALT, Crypt.crypt(hash.substring(3), DEFAULT_SALT))
                    : new CryptHash(hash.substring(0, hash.lastIndexOf('$')), hash));
        }
        knownHashes = mapBuilder.build();
    }

    @Override
    public boolean authenticate(final String username, final String password, final ServerSession serverSession) {
        final var found = knownHashes.get(username);
        return found != null && found.hash.equals(Crypt.crypt(password, found.salt));
    }
}
