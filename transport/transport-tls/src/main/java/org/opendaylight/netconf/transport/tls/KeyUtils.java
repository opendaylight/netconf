/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Objects;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

final class KeyUtils {
    private KeyUtils() {
        // utility class
    }

    static void validatePublicKey(final PublicKey publicKey, final Certificate certificate)
            throws UnsupportedConfigurationException {
        if (!Objects.equals(publicKey, certificate.getPublicKey())) {
            throw new UnsupportedConfigurationException("Certificate mismatches Public key");
        }
    }
}
