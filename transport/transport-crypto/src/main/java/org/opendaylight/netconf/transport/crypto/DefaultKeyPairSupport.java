/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import com.google.common.collect.Maps;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.AsymmetricKeyPairGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.EncryptedPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.HiddenPrivateKey;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 * An entity capable of translating a {@link AsymmetricKeyPairGrouping}s into a {@link KeyPair}.
 */
@Singleton
@Component
public final class DefaultKeyPairSupport implements KeyPairSupport {
    private final Map<PrivateKeyFormat, PrivateKeySupport<?>> privateKeySupports;
    private final Map<PublicKeyFormat, PublicKeySupport<?>> publicKeySupports;

    @Inject
    @Activate
    public DefaultKeyPairSupport(
            @Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption = ReferencePolicyOption.GREEDY)
            final List<PrivateKeySupport<?>> privateKeySupports,
            @Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption = ReferencePolicyOption.GREEDY)
            final List<PublicKeySupport<?>> publicKeySupports) {
        // FIXME: allow overlaps?
        this.privateKeySupports = Maps.uniqueIndex(privateKeySupports, PrivateKeySupport::format);
        this.publicKeySupports = Maps.uniqueIndex(publicKeySupports, PublicKeySupport::format);
    }

    @Override
    public KeyPair createKeyPair(final AsymmetricKeyPairGrouping config) throws UnsupportedConfigurationException {
        final var pubFormat = config.getPublicKeyFormat();
        if (pubFormat == null) {
            throw new UnsupportedConfigurationException("Missing public key format");
        }
        final var pubBytes = config.getPublicKey();
        if (pubBytes == null) {
            throw new UnsupportedConfigurationException("Missing public key");
        }
        final var privFormat = config.getPrivateKeyFormat();
        if (privFormat == null) {
            throw new UnsupportedConfigurationException("Missing private key format");
        }
        final var privType = config.getPrivateKeyType();
        if (privType == null) {
            throw new UnsupportedConfigurationException("Missing private key type");
        }

        final var pubSupport = publicKeySupports.get(pubFormat);
        if (pubSupport == null) {
            throw new UnsupportedConfigurationException("Unsupported public key format " + pubFormat);
        }
        final var privSupport = privateKeySupports.get(privFormat);
        if (privSupport == null) {
            throw new UnsupportedConfigurationException("Unsupported private key format " + privFormat);
        }

        final var privBytes = switch (privType) {
            case CleartextPrivateKey type -> {
                final var key = type.getCleartextPrivateKey();
                if (key == null) {
                    throw new UnsupportedConfigurationException("Missing cleartext private key");
                }
                yield key;
            }
            case EncryptedPrivateKey type ->
                throw new UnsupportedConfigurationException("Encrypted private keys are not supported");
            case HiddenPrivateKey type ->
                throw new UnsupportedConfigurationException("Hidden private keys are not supported");
            default -> throw new UnsupportedConfigurationException("Unsupported private key type " + privType);
        };

        final var privKey = privSupport.createKey(privBytes);
        return new KeyPair(pubSupport.createKey(privKey.getAlgorithm(), pubBytes), privKey);
    }
}
