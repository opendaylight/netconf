/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.Crypt;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.client.authentication.users.user.auth.type.Basic;

/**
 * {@link BasicAuthHandlerFactory} implementation for Basic Authorization.
 *
 * <p>Uses predefined (configured) collection of usernames with password hashes to authenticate/authorize users.
 */
public final class BasicAuthHandlerFactory implements AuthHandlerFactory {
    @NonNullByDefault
    private record CryptHash(String salt, String hash) {
        CryptHash {
            requireNonNull(salt);
            requireNonNull(hash);
        }
    }

    private static final Pattern CRYPT_HASH_PATTERN = Pattern.compile("""
        \\$0\\$.*\
        |\\$1\\$[a-zA-Z0-9./]{1,8}\\$[a-zA-Z0-9./]{22}\
        |\\$5\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{43}\
        |\\$6\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{86}""");
    private static final String DEFAULT_SALT = "$5$rounds=3500$default";

    private final ImmutableMap<String, CryptHash> knownHashes;

    private BasicAuthHandlerFactory(final ImmutableMap<String, CryptHash> knownHashes) {
        this.knownHashes = requireNonNull(knownHashes);
    }

    public static @Nullable BasicAuthHandlerFactory ofNullable(final HttpServerGrouping httpParams) {
        if (httpParams == null) {
            return null;
        }
        final var clientAuth = httpParams.getClientAuthentication();
        if (clientAuth == null) {
            return null;
        }

        // Basic authorization handler
        final var builder = ImmutableMap.<String, CryptHash>builder();
        clientAuth.nonnullUsers().nonnullUser().forEach((ignored, user) -> {
            if (user.getAuthType() instanceof Basic basicAuth) {
                final var basic = basicAuth.nonnullBasic();
                final var hashedPassword = basic.nonnullPassword().requireHashedPassword().getValue();
                if (!CRYPT_HASH_PATTERN.matcher(hashedPassword).matches()) {
                    throw new IllegalArgumentException("Invalid crypt hash string \"" + hashedPassword + '"');
                }
                final var cryptHash = hashedPassword.startsWith("$0$")
                    ? new CryptHash(DEFAULT_SALT, Crypt.crypt(hashedPassword.substring(3), DEFAULT_SALT))
                    : new CryptHash(hashedPassword.substring(0, hashedPassword.lastIndexOf('$')), hashedPassword);
                builder.put(basic.requireUsername(), cryptHash);
            }
        });
        final var knownHashes = builder.build();
        return knownHashes.isEmpty() ? null : new BasicAuthHandlerFactory(knownHashes);
    }

    @Override
    public AuthHandler<?> create() {
        // using String as authentication object: returning username if authenticated, null otherwise
        return new AbstractBasicAuthHandler<String>() {
            @Override
            protected String authenticate(final String username, final String password) {
                final var found = knownHashes.get(username);
                return found != null && found.hash.equals(Crypt.crypt(password, found.salt)) ? username : null;
            }
        };
    }
}
