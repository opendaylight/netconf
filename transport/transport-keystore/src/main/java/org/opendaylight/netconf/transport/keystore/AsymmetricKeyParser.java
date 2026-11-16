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
     * {@return a {@link KeyPair} for specified {@link InlineOrKeystore} configuration}
     *
     * @param input the {@link InlineOrKeystore} configuration
     * @throws UnsupportedConfigurationException if {@code input} represents an unsupported configuration
     */
    @CheckReturnValue
    public final KeyPair keyOf(final InlineOrKeystore input) throws UnsupportedConfigurationException {
        return switch (input) {
            case Inline inline -> inlineKeyOf(inline);
            case CentralKeystore central -> centralKeyOf(central);
            default -> throw new UnsupportedOperationException(
                "Unsupported configuration " + input.implementedCase().getCanonicalName());
        };
    }

    abstract KeyPair centralKeyOf(CentralKeystore input) throws UnsupportedConfigurationException;

    private static KeyPair inlineKeyOf(final Inline input) throws UnsupportedConfigurationException {
        final var inlineDefinition = input.getInlineDefinition();
        if (inlineDefinition == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + input);
        }
        return KeyPairParser.parseKeyPair(inlineDefinition);
    }

    @CheckReturnValue
    static final UnsupportedOperationException unsupportedConfiguration(final InlineOrKeystore input) {
        return new UnsupportedOperationException(
            "Unsupported configuration " + input.implementedCase().getCanonicalName());
    }

    @Override
    public abstract String toString();
}
