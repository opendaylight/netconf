/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.NoSuchElementException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PrivateKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKey;

/**
 * Cryptographic support for resolving {@link PrivateKeyGrouping}s.
 */
abstract class PrivateKeySupport {
    // Note: these are assumed to be thread-safe!
    private static final Map<PrivateKeyFormat, KeyFactory> KEY_FACTORIES;

    static {
        try {
            KEY_FACTORIES = Map.of(
                EcPrivateKeyFormat.VALUE, KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME),
                RsaPrivateKeyFormat.VALUE, KeyFactory.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME));
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Note: unchecked exceptions for ease of use
    final PrivateKey createPrivateKey(final PrivateKeyGrouping definition)
            throws NoSuchElementException, UnsupportedConfigurationException {
        final var keyFormat = definition.requirePrivateKeyFormat();
        final var keyFactory = KEY_FACTORIES.get(keyFormat);
        if (keyFactory == null) {
            throw new UnsupportedConfigurationException("Unsupported key format " + keyFormat);
        }

        final var keyType = definition.getPrivateKeyType();
        final var bytes = switch (keyType) {
            case null -> throw new UnsupportedConfigurationException("Missing private key type");
            case CleartextPrivateKey cleartext -> cleartext.requireCleartextPrivateKey();
            default -> throw new UnsupportedConfigurationException("Missing private key type " + keyType);
        };

        try {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (InvalidKeySpecException e) {
            throw new UnsupportedConfigurationException("Maformed private key", e);
        }
    }
}
