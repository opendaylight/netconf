/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import java.security.Provider;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A holder class providing access to {@link BouncyCastleProvider} and {@link BouncyCastlePQCProvider}. These are either
 * picked up from registered providers, or instantiated as singletons.
 */
@NonNullByDefault
final class BCHolder {
    /**
     * The pre-Quantum {@link BouncyCastleProvider}.
     */
    static final Provider PROV;
    /**
     * The post-Quantum {@link BouncyCastlePQCProvider}.
     */
    static final Provider PQC_PROV;

    static {
        final var preq = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        PROV = preq != null ? preq : new BouncyCastleProvider();

        final var postq = Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME);
        PQC_PROV = postq != null ? postq : new BouncyCastlePQCProvider();
    }

    private BCHolder() {
        // Hidden on purpose
    }
}
