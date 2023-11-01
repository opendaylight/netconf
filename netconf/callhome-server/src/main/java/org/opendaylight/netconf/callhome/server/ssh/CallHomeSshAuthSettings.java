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
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

/**
 * Authorization context for incoming call home sessions.
 *
 * @see CallHomeSshAuthProvider
 */
public interface CallHomeSshAuthSettings {

    /**
     * Unique client identifier this auth settings belongs to.
     *
     * @return identifier
     */
    String id();

    /**
     * Applies auth settings to Mina SSH Client Session.
     *
     * @param session {@code ClientSession} to which authorization parameters will be applied
     */
    void applyTo(ClientSession session);

    record DefaultAuthSettings(String id, String username, Collection<String> passwords, Collection<KeyPair> keyPairs)
        implements CallHomeSshAuthSettings {

        public DefaultAuthSettings {
            requireNonNull(id);
            requireNonNull(username);
            if ((passwords == null || passwords().isEmpty()) && (keyPairs == null || keyPairs.isEmpty())) {
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
