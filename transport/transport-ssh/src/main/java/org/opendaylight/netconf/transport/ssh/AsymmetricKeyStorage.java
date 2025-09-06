/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.security.KeyPair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.InlineOrKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.Inline;

/**
 * Base class for entities which can resolve asymmetric key configuration expressed as {@link InlineOrKeystore}.
 */
@NonNullByDefault
abstract class AsymmetricKeyStorage {
    /**
     * Global singleton which does not have any storage.
     */
    static final AsymmetricKeyStorage INLINE_ONLY = new AsymmetricKeyStorage() {
        @Override
        KeyPair resolveCentral(final CentralKeystore centralCase) throws UnsupportedConfigurationException {
            throw new UnsupportedConfigurationException("Central keystore not supported");
        }
    };

    final KeyPair resolveKeyPair(final InlineOrKeystore input) throws UnsupportedConfigurationException {
        return switch (input) {
            case CentralKeystore ref -> resolveCentral(ref);
            case Inline def -> resolveInline(def);
            default -> resolveAugmented(input);
       };
    }

    abstract KeyPair resolveCentral(CentralKeystore centralCase) throws UnsupportedConfigurationException;

    KeyPair resolveAugmented(InlineOrKeystore augmentedCase) throws UnsupportedConfigurationException {
        throw new UnsupportedConfigurationException("Unsupported asymmetric key case " + augmentedCase);
    }

    private static final KeyPair resolveInline(final Inline inlineCase) throws UnsupportedConfigurationException {
        final var inlineDef = inlineCase.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inlineCase);
        }
        return KeyUtils.extractKeyPair(inlineDef);
    }


}
