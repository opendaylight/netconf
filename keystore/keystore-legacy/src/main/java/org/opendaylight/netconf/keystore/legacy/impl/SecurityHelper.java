/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import com.google.common.annotations.VisibleForTesting;
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
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.util.encoders.DecoderException;
import org.eclipse.jdt.annotation.NonNull;

public final class SecurityHelper {
    private CertificateFactory certFactory;
    private Provider bcProv;

    static @NonNull PrivateKey generatePrivateKey(final byte[] privateKeyBytes, final String algorithm)
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

    static @NonNull KeyPair generateKeyPair(final byte[] privateKeyBytes, final byte[] publicKeyBytes,
            final String algorithm) throws GeneralSecurityException {
        final var privateKey = generatePrivateKey(privateKeyBytes, algorithm);
        final var publicKey = KeyFactory.getInstance(algorithm).generatePublic(
            new X509EncodedKeySpec(publicKeyBytes));
        return new KeyPair(publicKey, privateKey);
    }

    @VisibleForTesting
    public @NonNull KeyPair decodePrivateKey(final String privateKey, final String passphrase) throws IOException {
        if (bcProv == null) {
            final var prov = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
            bcProv = prov != null ? prov : new BouncyCastleProvider();
        }
        if (privateKey == null) {
            throw new IOException("No private key present");
        }

        final PEMKeyPair keyPair;

        try (var keyReader = new PEMParser(new StringReader(privateKey.replace("\\n", "\n")))) {
            final var obj = keyReader.readObject();

            keyPair = switch (obj) {
                case PEMEncryptedKeyPair encrypted -> encrypted.decryptKeyPair(new JcePEMDecryptorProviderBuilder()
                    .setProvider(bcProv)
                    .build(passphrase.toCharArray()));
                case PEMKeyPair plain -> plain;
                case null -> throw new IOException("Invalid private key");
                default -> throw new IOException("Unhandled private key " + obj.getClass());
            };
        } catch (DecoderException e) {
            throw new IOException("Invalid input.", e);
        }

        return new JcaPEMKeyConverter().getKeyPair(keyPair);
    }

    @VisibleForTesting
    public static @NonNull X509Certificate decodeCertificate(final String certificate)
            throws IOException, GeneralSecurityException {
        try (var certReader = new PEMParser(new StringReader(certificate.replace("\\n", "\n")))) {
            final var obj = certReader.readObject();

            if (obj instanceof X509CertificateHolder cert) {
                return new JcaX509CertificateConverter().getCertificate(cert);
            } else {
                throw new IOException("Unhandled certificate " + obj.getClass());
            }
        } catch (DecoderException e) {
            throw new IOException("Invalid input.", e);
        }
    }
}
