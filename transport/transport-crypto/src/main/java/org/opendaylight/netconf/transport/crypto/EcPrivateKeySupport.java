/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;

/**
 * {@link PrivateKeySupport} for {@link EcPrivateKeyFormat}.
 */
// FIXME: turn this into a component once we have Dagger
@NonNullByDefault
final class EcPrivateKeySupport implements PrivateKeySupport<EcPrivateKeyFormat> {
    EcPrivateKeySupport() {
        // Nothing else
    }

    @Override
    public EcPrivateKeyFormat format() {
        return EcPrivateKeyFormat.VALUE;
    }

    @Override
    public PrivateKey createKey(byte[] bytes) throws UnsupportedConfigurationException {
        try {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (InvalidKeySpecException e) {
            throw new UnsupportedConfigurationException("Invalid ECPrivateKey", e);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("ECPrivateKey is not supported", e);
        }
    }
}
