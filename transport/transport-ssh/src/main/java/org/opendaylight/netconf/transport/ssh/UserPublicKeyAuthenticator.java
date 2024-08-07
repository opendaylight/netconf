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

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import org.opendaylight.netconf.shaded.sshd.server.auth.AsyncAuthException;
import org.opendaylight.netconf.shaded.sshd.server.auth.hostbased.HostBasedAuthenticator;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.opendaylight.netconf.shaded.sshd.server.session.ServerSession;

final class UserPublicKeyAuthenticator implements HostBasedAuthenticator, PublickeyAuthenticator {
    private final Map<String, List<PublicKey>> userHostPublicKeyMap;

    UserPublicKeyAuthenticator(final Map<String, List<PublicKey>> userHostPublicKeyMap) {
        this.userHostPublicKeyMap = requireNonNull(userHostPublicKeyMap);
        checkArgument(!userHostPublicKeyMap.isEmpty(), "userHostPublicKeyMap should not be empty");
    }

    @Override // HostBasedAuthenticator
    public boolean authenticate(final ServerSession serverSession, final String username, final PublicKey clientHostKey,
            final String clientHostName, final String clientUsername, final List<X509Certificate> certificates) {
        // NB hostname is not checked bc client hostname is not delivered vie configuration
        // once ietf model is updated and hostname is provided then HostBasedAuthenticator implementation
        // needs to be extracted to separate class
        return userHasMatchingKey(username, clientHostKey);
    }

    @Override // PublickeyAuthenticator
    public boolean authenticate(final String username, final PublicKey publicKey, final ServerSession serverSession)
            throws AsyncAuthException {
        return userHasMatchingKey(username, publicKey);
    }

    private boolean userHasMatchingKey(final String username, final PublicKey publicKey) {
        final var keys = userHostPublicKeyMap.get(username);
        return keys != null && keys.contains(publicKey);
    }
}
