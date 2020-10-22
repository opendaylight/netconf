/*
 * Copyright (c) 2020 Al-soft and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.NativeNetconfKeystoreImpl.KeyStoreUpdateStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;

/**
 * Base interface for sharing specific implementation in topology with dependent instance for communication.
 *
 */
public interface NativeNetconfKeystore {

    /**
     * Get credentials for specific key id.
     *
     * @param keyId Credential key id
     * @return credential
     */
    Optional<KeyCredential> getKeypairFromId(String keyId);

    /**
     * Using private keys and trusted certificates to create a new JDK <code>KeyStore</code> which will be used by TLS
     * clients to create <code>SSLEngine</code>. The private keys are essential to create JDK <code>KeyStore</code>
     * while the trusted certificates are optional.
     *
     * @return A JDK KeyStore object
     * @throws GeneralSecurityException If any security exception occurred
     * @throws IOException              If there is an I/O problem with the keystore data
     */
    KeyStore getJavaKeyStore() throws GeneralSecurityException, IOException;

    /**
     * Using private keys and trusted certificates to create a new JDK <code>KeyStore</code> which will be used by TLS
     * clients to create <code>SSLEngine</code>. The private keys are essential to create JDK <code>KeyStore</code>
     * while the trusted certificates are optional.
     *
     * @param allowedKeys Set of keys to include during KeyStore generation, empty set will create a KeyStore with all
     *                    possible keys.
     * @return A JDK KeyStore object
     * @throws GeneralSecurityException If any security exception occurred
     * @throws IOException              If there is an I/O problem with the keystore data
     */
    KeyStore getJavaKeyStore(Set<String> allowedKeys) throws GeneralSecurityException, IOException;

    /**
     * Update key credentials.
     *
     * @param data {@link Keystore} data
     */
    void updateKeyCredentials(Keystore data);

    /**
     * Update changed private key.
     *
     * @param data   Changed private key
     * @param status type of change
     */
    void onPrivateKeyChanged(PrivateKey data, KeyStoreUpdateStatus status);

    /**
     * Update changed trusted certificate.
     *
     * @param data   Changed trusted certificate
     * @param status type of change
     */
    void onTrustedCertificateChanged(TrustedCertificate data, KeyStoreUpdateStatus status);

}
