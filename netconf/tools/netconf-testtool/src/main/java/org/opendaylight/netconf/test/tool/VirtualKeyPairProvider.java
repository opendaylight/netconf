/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Key provider that allows to create exactly one key-pair in memory (key-pair is not persisted in memory).
 * If the key-pair has already been generated, the existing one is used.
 */
public class VirtualKeyPairProvider implements KeyPairProvider {
    private static final Logger LOG = LoggerFactory.getLogger(VirtualKeyPairProvider.class);

    private KeyPair generatedKeyPair;
    private String algorithm = KeyUtils.RSA_ALGORITHM;
    private AlgorithmParameterSpec keySpecification;
    private Integer keySize;

    /**
     * Creation of the key-provider with default settings - RSA algorithm with key length of 2048.
     *
     * @see VirtualKeyPairProvider#VirtualKeyPairProvider(String, AlgorithmParameterSpec, Integer)
     */
    VirtualKeyPairProvider() {
    }

    /**
     * Creation of the key-provider with explicitly defined algorithmic settings.
     *
     * @param algorithm        Algorithm that is used for generation of a new key-pair. Currently supported algorithms:
     *                         {@link KeyUtils#RSA_ALGORITHM}, {@link KeyUtils#DSS_ALGORITHM},
     *                         {@link KeyUtils#EC_ALGORITHM}.
     * @param keySpecification Algorithm-specific settings.
     * @param keySize          To be generated key length (must be adjusted against selected algorithm).
     */
    @SuppressWarnings("WeakerAccess")
    VirtualKeyPairProvider(final String algorithm,
                           final AlgorithmParameterSpec keySpecification,
                           final Integer keySize) {
        this.algorithm = algorithm;
        this.keySpecification = keySpecification;
        this.keySize = keySize;
    }

    @Override
    public synchronized Iterable<KeyPair> loadKeys(final SessionContext session) {
        if (generatedKeyPair == null) {
            try {
                generatedKeyPair = generateKeyPair();
            } catch (GeneralSecurityException e) {
                LOG.error("Cannot generate key with algorithm '{}', key specification '{}', and key size '{}'.",
                        algorithm, keySpecification, keySize, e);
                throw new IllegalArgumentException("An error occurred during generation of a new ke pair.", e);
            }
        }
        return Collections.singleton(generatedKeyPair);
    }

    /**
     * Generating of the new key-pair using specified parameters - algorithm, key length, and key specification.
     *
     * @return Generated key-pair.
     * @throws GeneralSecurityException If the generation process fails because of the wrong input parameters.
     */
    private KeyPair generateKeyPair() throws GeneralSecurityException {
        final KeyPairGenerator generator = SecurityUtils.getKeyPairGenerator(algorithm);
        if (keySpecification != null) {
            generator.initialize(keySpecification);
        } else if (keySize != null) {
            generator.initialize(keySize);
        } else if (KeyUtils.EC_ALGORITHM.equals(algorithm)) {
            int numCurves = ECCurves.SORTED_KEY_SIZE.size();
            ECCurves curve = ECCurves.SORTED_KEY_SIZE.get(numCurves - 1);
            generator.initialize(curve.getParameters());
        }
        return generator.generateKeyPair();
    }
}