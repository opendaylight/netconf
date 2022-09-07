/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls.config;

import static java.util.Objects.requireNonNull;

/**
 * Represents content of ietf-keystore:local-or-keystore-asymmetric-key-grouping.
 *
 * @param privateKeyFormat private key format
 * @param privateKeyBytes private key bytes
 * @param publicKeyFormat public key format
 * @param publicKeyBytes public key bytes
 */
record KeyPairInfo(PrivateKeyFormat privateKeyFormat, byte[] privateKeyBytes,
                   PublicKeyFormat publicKeyFormat, byte[] publicKeyBytes) {

    enum PrivateKeyFormat {
        EC, RSA
    }

    enum PublicKeyFormat {
        SUBJECT_INFO, SSH
    }

    KeyPairInfo {
        requireNonNull(privateKeyFormat);
        requireNonNull(privateKeyBytes);
        requireNonNull(publicKeyFormat);
        requireNonNull(publicKeyBytes);
    }
}
