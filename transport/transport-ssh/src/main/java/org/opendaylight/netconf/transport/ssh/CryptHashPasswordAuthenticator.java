/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.Crypt;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.server.auth.AsyncAuthException;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.PasswordAuthenticator;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.PasswordChangeRequiredException;
import org.opendaylight.netconf.shaded.sshd.server.session.ServerSession;

final class CryptHashPasswordAuthenticator implements PasswordAuthenticator {

    private static final Pattern CRYPT_HASH_PATTERN = Pattern.compile(
            "\\$0\\$.*" // clear text
                    + "|\\$1\\$[a-zA-Z0-9./]{1,8}\\$[a-zA-Z0-9./]{22}" // MD5
                    + "|\\$5\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{43}" // SHA-256
                    + "|\\$6\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{86}"); // SHA-512

    private static final String DEFAULT_SALT = "$5$rounds=3500$default";

    final Map<String, CryptHashValidator> userValidatorMap;

    CryptHashPasswordAuthenticator(final @NonNull Map<String, String> userHashes) {
        requireNonNull(userHashes);
        checkArgument(!userHashes.isEmpty(), "Hashes map should not be empty");
        final var mapBuilder = ImmutableMap.<String, CryptHashValidator>builder();
        for (var entry : userHashes.entrySet()) {
            mapBuilder.put(entry.getKey(), new CryptHashValidator(entry.getValue()));
        }
        userValidatorMap = mapBuilder.build();
    }

    @Override
    public boolean authenticate(final String username, final String password, final ServerSession serverSession)
            throws PasswordChangeRequiredException, AsyncAuthException {
        return Optional.ofNullable(userValidatorMap.get(username))
                .map(validator -> validator.isValid(password)).orElse(false);
    }

    private static class CryptHashValidator {
        final String salt;
        final String cryptHash;

        CryptHashValidator(final String cryptHash) {
            requireNonNull(cryptHash);
            checkArgument(CRYPT_HASH_PATTERN.matcher(cryptHash).matches(), "Not a valid crypt hash string");
            if (cryptHash.startsWith("$0$")) {
                // clear text password, build a hash using default salt
                this.salt = DEFAULT_SALT;
                this.cryptHash = Crypt.crypt(cryptHash.substring(3), DEFAULT_SALT);
            } else {
                this.salt = cryptHash.substring(0, cryptHash.lastIndexOf('$'));
                this.cryptHash = cryptHash;
            }
        }

        boolean isValid(final String password) {
            return cryptHash.equals(Crypt.crypt(password, salt));
        }
    }
}
