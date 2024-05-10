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
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.eclipse.jdt.annotation.NonNull;

final class SecurityHelper {
    private CertificateFactory certFactory;
    private KeyFactory dsaFactory;
    private KeyFactory rsaFactory;
    private Provider bcProv;

    @NonNull PrivateKey generatePrivateKey(final byte[] privateKey) throws GeneralSecurityException {
        final var keySpec = new PKCS8EncodedKeySpec(privateKey);

        if (rsaFactory == null) {
            rsaFactory = KeyFactory.getInstance("RSA");
        }
        try {
            return rsaFactory.generatePrivate(keySpec);
        } catch (InvalidKeySpecException ignore) {
            // Ignored
        }

        if (dsaFactory == null) {
            dsaFactory = KeyFactory.getInstance("DSA");
        }
        return dsaFactory.generatePrivate(keySpec);
    }


    @NonNull X509Certificate generatePemCertificate(final String certificate) throws IOException {
        if (bcProv == null) {
            final var prov = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
            bcProv = prov != null ? prov : new BouncyCastleProvider();
        }

        try (var keyReader = new PEMParser(new StringReader(certificate.replace("\\n", "\n")))) {
            final var obj = keyReader.readObject();

            if (obj instanceof X509CertificateHolder encrypted) {
                return new JcaX509CertificateConverter().getCertificate(encrypted);
            } else {
                throw new IOException("Unhandled private key " + obj.getClass());
            }
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
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