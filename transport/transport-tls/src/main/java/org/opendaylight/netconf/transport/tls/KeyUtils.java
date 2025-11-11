/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

final class KeyUtils {
    private KeyUtils() {
        // utility class
    }

    static void validateKeyPair(final PublicKey publicKey, final PrivateKey privateKey)
            throws UnsupportedConfigurationException {
        final String signAlgorithm;
        if (privateKey instanceof RSAPrivateKey) {
            signAlgorithm = "SHA256withRSA";
        } else if (privateKey instanceof ECPrivateKey) {
            signAlgorithm = "SHA256withECDSA";
        } else {
            throw new UnsupportedConfigurationException("Unsupported key type " + privateKey);
        }
        try {
            // test data
            final byte[] original = new byte[1024];
            ThreadLocalRandom.current().nextBytes(original);
            // sign using the private key
            final Signature signature = Signature.getInstance(signAlgorithm);
            signature.initSign(privateKey);
            signature.update(original);
            final byte[] signed = signature.sign();
            // verify using the public key
            signature.initVerify(publicKey);
            signature.update(original);
            if (!signature.verify(signed)) {
                throw new UnsupportedConfigurationException("Private key mismatches Public key");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new UnsupportedConfigurationException("Key pair validation failed", e);
        }
    }

    static void validatePublicKey(final PublicKey publicKey, final Certificate certificate)
            throws UnsupportedConfigurationException {
        if (!Objects.equals(publicKey, certificate.getPublicKey())) {
            throw new UnsupportedConfigurationException("Certificate mismatches Public key");
        }
    }
}
