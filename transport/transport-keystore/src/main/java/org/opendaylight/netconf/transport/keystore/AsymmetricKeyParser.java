/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.keystore;

import com.google.common.annotations.Beta;
import com.google.errorprone.annotations.CheckReturnValue;
import java.security.KeyPair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.crypto.KeyPairParser;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.InlineOrKeystoreAsymmetricKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.InlineOrKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.Inline;

/**
 * Support for parsing public/private key pairs encoded as {@link InlineOrKeystore}.
 *
 * <p>This class is considered a Beta-level API for internal use. It can change incompatibly between minor releases.
 *
 * @since 12.0.3
 */
@Beta
@NonNullByDefault
public abstract sealed class AsymmetricKeyParser permits InlineAsymmetricKeyParser, KeystoreAsymmetricKeyParser {
    AsymmetricKeyParser() {
        // hidden on purpose
    }

    /**
     * {@return a {@link KeyPair} for specified {@link InlineOrKeystoreAsymmetricKeyGrouping} configuration}
     *
     * @param grouping the {@link InlineOrKeystoreAsymmetricKeyGrouping} configuration
     * @throws UnsupportedConfigurationException if {@code input} represents an unsupported configuration
     */
    @CheckReturnValue
    public final KeyPair keyOf(final InlineOrKeystoreAsymmetricKeyGrouping grouping)
            throws UnsupportedConfigurationException {
        final var inlineOrKeystore = grouping.getInlineOrKeystore();
        if (inlineOrKeystore == null) {
            throw new UnsupportedConfigurationException("Missing inline-or-keystore in " + grouping);
        }
        return keyOf(inlineOrKeystore);
    }

    /**
     * {@return a {@link KeyPair} for specified {@link InlineOrKeystore} configuration}
     *
     * @param inlineOrKeystore the {@link InlineOrKeystore} configuration
     * @throws UnsupportedConfigurationException if {@code input} represents an unsupported configuration
     */
    @CheckReturnValue
    public final KeyPair keyOf(final InlineOrKeystore inlineOrKeystore) throws UnsupportedConfigurationException {
        return switch (inlineOrKeystore) {
            case Inline inline -> inlineKeyOf(inline);
            case CentralKeystore central -> centralKeyOf(central);
            default -> throw unsupportedConfiguration(inlineOrKeystore);
        };
    }

    abstract KeyPair centralKeyOf(CentralKeystore central) throws UnsupportedConfigurationException;

    private static KeyPair inlineKeyOf(final Inline inline) throws UnsupportedConfigurationException {
        final var inlineDefinition = inline.getInlineDefinition();
        if (inlineDefinition == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inline);
        }
        return KeyPairParser.parseKeyPair(inlineDefinition);
    }

    @CheckReturnValue
    static final UnsupportedConfigurationException unsupportedConfiguration(final InlineOrKeystore input) {
        return new UnsupportedConfigurationException(
            "Unsupported configuration " + input.implementedCase().getCanonicalName());
    }

    @Override
    public abstract String toString();
}
