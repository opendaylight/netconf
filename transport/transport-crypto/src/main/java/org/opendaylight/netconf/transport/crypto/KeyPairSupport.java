/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import com.google.common.annotations.Beta;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yangtools.binding.BaseIdentity;

/**
 * Support for public/private key pairs in their various forms. This class is considered a Beta-level API for internal
 * use. It can change incompatibly between minor releases.
 *
 * @since 10.0.1
 */
@Beta
@NonNullByDefault
public final class KeyPairSupport {
    private KeyPairSupport() {
        // Hidden on purpose
    }

    public static KeyPair parseKeyPair(final AsymmetricKeyPairGrouping keyPair)
            throws UnsupportedConfigurationException {
        final var privKeyFactory = getKeyFactory(keyPair.requirePrivateKeyFormat());

    }

    private static KeyFactory getKeyFactory(final PrivateKeyFormat format) throws UnsupportedConfigurationException {
        final String algorithm;
        if (EcPrivateKeyFormat.VALUE.equals(format)) {
            algorithm = "EC";
        } else if (RsaPrivateKeyFormat.VALUE.equals(format)) {
            algorithm = "RSA";
        } else {
            throw new UnsupportedConfigurationException("Unsupported private key format " + format);
        }

        return getKeyFactory(format, algorithm);
    }

    private static KeyFactory getKeyFactory(final BaseIdentity format, final String algorithm)
            throws UnsupportedConfigurationException {
        try {
            return KeyFactory.getInstance(algorithm, BCProviderLoader.PROVIDER_NAME);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new UnsupportedConfigurationException("Unsupported private key format " + format, e);
        }
    }
}
