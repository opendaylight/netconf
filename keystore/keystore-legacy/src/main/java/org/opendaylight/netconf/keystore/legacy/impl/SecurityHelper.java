/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.eclipse.jdt.annotation.NonNull;

final class SecurityHelper {
    private CertificateFactory certFactory;
    private Provider bcProv;

    @NonNull PrivateKey generatePrivateKey(final byte[] privateKeyBytes, final String algorithm)
            throws GeneralSecurityException {
        return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
    }

    @NonNull X509Certificate generateCertificate(final byte[] certificate) throws GeneralSecurityException {
        // TODO: https://stackoverflow.com/questions/43809909/is-certificatefactory-getinstancex-509-thread-safe
        //        indicates this is thread-safe in most cases, but can we get a better assurance?
        if (certFactory == null) {
            certFactory = CertificateFactory.getInstance("X.509");
        }
        return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certificate));
    }

    @NonNull KeyPair generateKeyPair(final byte[] privateKeyBytes, final byte[] publicKeyBytes, final String algorithm)
        throws GeneralSecurityException {
        final var privateKey = generatePrivateKey(privateKeyBytes, algorithm);
        final var publicKey = KeyFactory.getInstance(algorithm).generatePublic(
            new X509EncodedKeySpec(publicKeyBytes));
        return new KeyPair(publicKey, privateKey);
    }

    @NonNull KeyPair decodePrivateKey(final String privateKey, final String passphrase) throws IOException {
        if (bcProv == null) {
            final var prov = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
            bcProv = prov != null ? prov : new BouncyCastleProvider();
        }

        try (var keyReader = new PEMParser(new StringReader(privateKey.replace("\\n", "\n")))) {
            final var obj = keyReader.readObject();

            final PEMKeyPair keyPair;
            if (obj instanceof PEMEncryptedKeyPair encrypted) {
                keyPair = encrypted.decryptKeyPair(new JcePEMDecryptorProviderBuilder()
                    .setProvider(bcProv)
                    .build(passphrase.toCharArray()));
            } else if (obj instanceof PEMKeyPair plain) {
                keyPair = plain;
            } else {
                throw new IOException("Unhandled private key " + obj.getClass());
            }

            return new JcaPEMKeyConverter().getKeyPair(keyPair);
        }
    }
}