/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.api;

import static java.util.Objects.requireNonNull;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * A {@link PrivateKey} which has as an attached {@link X509Certificate} chain.
 *
 * @param key the private key
 * @param certificateChain the certification chain of the private key
 */
@NonNullByDefault
public record CertifiedPrivateKey(
        PrivateKey key,
        List<X509Certificate> certificateChain) implements Immutable {
    public CertifiedPrivateKey {
        requireNonNull(key);
        certificateChain = List.copyOf(certificateChain);
        if (certificateChain.isEmpty()) {
            throw new IllegalArgumentException("Certificate chain must not be empty");
        }
    }
}