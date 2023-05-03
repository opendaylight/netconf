/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2023 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;

public interface NetconfKeystoreAdapter {

    Optional<KeyCredential> getKeypairFromId(String keyId);

    /**
     * Using private keys and trusted certificates to create a new JDK <code>KeyStore</code> which
     * will be used by TLS clients to create <code>SSLEngine</code>. The private keys are essential
     * to create JDK <code>KeyStore</code> while the trusted certificates are optional.
     *
     * @return A JDK KeyStore object
     * @throws GeneralSecurityException If any security exception occurred
     * @throws IOException If there is an I/O problem with the keystore data
     */
    default KeyStore getJavaKeyStore() throws GeneralSecurityException, IOException {
        return getJavaKeyStore(Set.of());
    }

    /**
     * Using private keys and trusted certificates to create a new JDK <code>KeyStore</code> which
     * will be used by TLS clients to create <code>SSLEngine</code>. The private keys are essential
     * to create JDK <code>KeyStore</code> while the trusted certificates are optional.
     *
     * @param allowedKeys Set of keys to include during KeyStore generation, empty set will create
     *                   a KeyStore with all possible keys.
     * @return A JDK KeyStore object
     * @throws GeneralSecurityException If any security exception occurred
     * @throws IOException If there is an I/O problem with the keystore data
     */
    KeyStore getJavaKeyStore(Set<String> allowedKeys) throws GeneralSecurityException, IOException;
}