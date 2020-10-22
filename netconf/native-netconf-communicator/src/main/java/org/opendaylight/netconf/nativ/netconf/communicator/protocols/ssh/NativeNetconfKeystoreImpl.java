/*
 * Copyright (c) 2020 ... . and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;

public class NativeNetconfKeystoreImpl implements NativeNetconfKeystore {

    private final Map<String, KeyCredential> pairs = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, PrivateKey> privateKeys = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, TrustedCertificate> trustedCertificates = Collections.synchronizedMap(new HashMap<>());

    @Override
    public Optional<KeyCredential> getKeypairFromId(final String keyId) {
        return Optional.ofNullable(pairs.get(keyId));
    }

    @Override
    public KeyStore getJavaKeyStore() throws GeneralSecurityException, IOException {
        return getJavaKeyStore(Collections.emptySet());
    }

    @Override
    public KeyStore getJavaKeyStore(final Set<String> allowedKeys) throws GeneralSecurityException, IOException {
        requireNonNull(allowedKeys);

        final KeyStore keyStore = KeyStore.getInstance("JKS");

        keyStore.load(null, null);

        synchronized (privateKeys) {
            if (privateKeys.isEmpty()) {
                throw new KeyStoreException("No keystore private key found");
            }

            for (final Map.Entry<String, PrivateKey> entry : privateKeys.entrySet()) {
                if (!allowedKeys.isEmpty() && !allowedKeys.contains(entry.getKey())) {
                    continue;
                }
                final java.security.PrivateKey key = getJavaPrivateKey(entry.getValue().getData());

                final List<X509Certificate> certificateChain = getCertificateChain(
                        entry.getValue().getCertificateChain().toArray(new String[0]));
                if (certificateChain.isEmpty()) {
                    throw new CertificateException("No certificate chain associated with private key found");
                }

                keyStore.setKeyEntry(entry.getKey(), key, "".toCharArray(),
                        certificateChain.stream().toArray(Certificate[]::new));
            }
        }

        synchronized (trustedCertificates) {
            for (final Map.Entry<String, TrustedCertificate> entry : trustedCertificates.entrySet()) {
                final List<X509Certificate> x509Certificates = getCertificateChain(
                        new String[] { entry.getValue().getCertificate() });

                keyStore.setCertificateEntry(entry.getKey(), x509Certificates.get(0));
            }
        }

        return keyStore;
    }

    @Override
    public void updateKeyCredentials(final Keystore data) {
        pairs.clear();
        if (data != null) {
            data.nonnullKeyCredential().values().forEach(pair -> pairs.put(pair.key().getKeyId(), pair));
        }
    }

    @Override
    public void onPrivateKeyChanged(final PrivateKey data, final KeyStoreUpdateStatus status) {
        switch (status) {
            case PUT:
                privateKeys.put(data.getName(), data);
                break;
            case DELETE:
                privateKeys.remove(data.getName());
                break;
            default:
                break;
        }
    }

    @Override
    public void onTrustedCertificateChanged(final TrustedCertificate data, final KeyStoreUpdateStatus status) {
        switch (status) {
            case PUT:
                trustedCertificates.put(data.getName(), data);
                break;
            case DELETE:
                trustedCertificates.remove(data.getName());
                break;
            default:
                break;
        }
    }

    private static java.security.PrivateKey getJavaPrivateKey(final String base64PrivateKey)
            throws GeneralSecurityException {
        final byte[] encodedKey = base64Decode(base64PrivateKey);
        final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedKey);
        java.security.PrivateKey key;

        try {
            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            key = keyFactory.generatePrivate(keySpec);
        } catch (final InvalidKeySpecException ignore) {
            final KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            key = keyFactory.generatePrivate(keySpec);
        }

        return key;
    }

    private static List<X509Certificate> getCertificateChain(final String[] base64Certificates)
            throws GeneralSecurityException {
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final List<X509Certificate> certificates = new ArrayList<>();

        for (final String cert : base64Certificates) {
            final byte[] buffer = base64Decode(cert);
            certificates.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(buffer)));
        }

        return certificates;
    }

    private static byte[] base64Decode(final String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public enum KeyStoreUpdateStatus {
        PUT, DELETE
    }
}
