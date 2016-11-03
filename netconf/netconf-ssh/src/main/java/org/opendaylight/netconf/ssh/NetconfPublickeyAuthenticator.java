/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.ssh;

import com.google.common.collect.Sets;
import java.security.PublicKey;
import java.util.Set;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfPublickeyAuthenticator implements PublickeyAuthenticator {

    private static final transient Logger LOG = LoggerFactory.getLogger(NetconfPublickeyAuthenticator.class);

    private final Set<PublicKey> authorizedPublicKey;

    public NetconfPublickeyAuthenticator(final String authorizedKeysPath) {
        authorizedPublicKey = parseAuthorizedKeys(authorizedKeysPath);
    }

    public NetconfPublickeyAuthenticator(final Set<PublicKey> authorizedPublicKey) {
        this.authorizedPublicKey = authorizedPublicKey;
    }

    private Set<PublicKey> parseAuthorizedKeys(final String authorizedKeysPath) {
        final AuthorizedKeysDecoder authorizedKeysDecoder = new AuthorizedKeysDecoder();
        Set<PublicKey> authorizedPublicKey = Sets.newHashSet();
        try {
            authorizedPublicKey = authorizedKeysDecoder.parseAuthorizedKeys(authorizedKeysPath);
        } catch (final Exception exception) {
            LOG.error("Cannot parse authorized key file '{}': {}", authorizedKeysPath, exception);
        }
        return authorizedPublicKey;
    }

    @Override
    public boolean authenticate(final String username, final PublicKey publicKey, final ServerSession session) {
        final Boolean clientPublickeyExists = authorizedPublicKey.contains(publicKey);
        if (!clientPublickeyExists) {
            LOG.error("{}: Failed to authentificate user '{}'. Unknown public key.",
                    session.getIoSession().getRemoteAddress(), username);

        }
        return clientPublickeyExists;
    }

}