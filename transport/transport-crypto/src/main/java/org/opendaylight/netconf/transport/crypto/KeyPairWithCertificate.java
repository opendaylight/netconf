/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import java.security.KeyPair;
import java.security.cert.Certificate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.AsymmetricKeyPairWithCertGrouping;

/**
 * A {@link KeyPair} and a {@link Certificate}, i.e. the equivalent of {@link AsymmetricKeyPairWithCertGrouping}.
 *
 * @param keyPair the {@link KeyPair}
 * @param certificate the {@link Certificate}
 */
// Note: package-private on purpose.
@NonNullByDefault
public record KeyPairWithCertificate(KeyPair keyPair, Certificate certificate) {
    /**
     * Default constructor.
     *
     * @param keyPair the {@link KeyPair}
     * @param certificate the {@link Certificate}
     * @throws IllegalArgumentException if the key pair does not match the certificate
     */
    public KeyPairWithCertificate {
        // FIXME: NETCONF-1543: better key comparison
        if (!keyPair.getPublic().equals(certificate.getPublicKey())) {
            throw new IllegalArgumentException("Certificate does not match the public key");
        }
    }
}