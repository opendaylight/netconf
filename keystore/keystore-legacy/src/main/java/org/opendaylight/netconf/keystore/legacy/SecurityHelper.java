/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.eclipse.jdt.annotation.NonNull;

public final class SecurityHelper {
    private CertificateFactory certFactory;
    private KeyFactory dsaFactory;
    private KeyFactory rsaFactory;

    public @NonNull PrivateKey getJavaPrivateKey(final String base64PrivateKey) throws GeneralSecurityException {
        final var keySpec = new PKCS8EncodedKeySpec(base64Decode(base64PrivateKey));

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

    public @NonNull X509Certificate getCertificate(final String base64Certificate) throws GeneralSecurityException {
        // TODO: https://stackoverflow.com/questions/43809909/is-certificatefactory-getinstancex-509-thread-safe
        //        indicates this is thread-safe in most cases, but can we get a better assurance?
        if (certFactory == null) {
            certFactory = CertificateFactory.getInstance("X.509");
        }
        return (X509Certificate) certFactory.generateCertificate(
            new ByteArrayInputStream(base64Decode(base64Certificate)));
    }

    private static byte[] base64Decode(final String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(StandardCharsets.US_ASCII));
    }

}