/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.keystore.api.CertifiedPrivateKey;
import org.opendaylight.yangtools.concepts.Immutable;

@NonNullByDefault
public record NetconfKeystore(
        Map<String, CertifiedPrivateKey> privateKeys,
        Map<String, X509Certificate> trustedCertificates,
        Map<String, KeyPair> credentials) implements Immutable {
    public static final NetconfKeystore EMPTY = new NetconfKeystore(Map.of(), Map.of(), Map.of());

    public NetconfKeystore {
        privateKeys = Map.copyOf(privateKeys);
        trustedCertificates = Map.copyOf(trustedCertificates);
        credentials = Map.copyOf(credentials);
    }
}
