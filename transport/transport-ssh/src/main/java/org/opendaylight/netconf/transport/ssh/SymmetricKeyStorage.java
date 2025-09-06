/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import javax.crypto.SecretKey;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.symmetric.key.grouping.InlineOrKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.symmetric.key.grouping.inline.or.keystore.CentralKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.symmetric.key.grouping.inline.or.keystore.Inline;

/**
 * Base class for entities which can resolve symmetric key configuration expressed as {@link InlineOrKeystore}.
 */
@NonNullByDefault
abstract class SymmetricKeyStorage {
    /**
     * Global singleton which does not have any storage.
     */
    static final SymmetricKeyStorage INLINE_ONLY = new SymmetricKeyStorage() {
        @Override
        SecretKey resolveCentral(final CentralKeystore centralCase) throws UnsupportedConfigurationException {
            throw new UnsupportedConfigurationException("Central keystore not supported");
        }
    };

    final SecretKey resolveKey(final InlineOrKeystore input) throws UnsupportedConfigurationException {
        return switch (input) {
            case CentralKeystore ref -> resolveCentral(ref);
            case Inline def -> resolveInline(def);
            default -> resolveAugmented(input);
       };
    }

    abstract SecretKey resolveCentral(CentralKeystore centralCase) throws UnsupportedConfigurationException;

    SecretKey resolveAugmented(InlineOrKeystore augmentedCase) throws UnsupportedConfigurationException {
        throw new UnsupportedConfigurationException("Unsupported symmetric key case " + augmentedCase);
    }

    private static final SecretKey resolveInline(final Inline inlineCase) throws UnsupportedConfigurationException {
        final var inlineDef = inlineCase.getInlineDefinition();
        if (inlineDef == null) {
            throw new UnsupportedConfigurationException("Missing inline definition in " + inlineCase);
        }

        // FIXME: implement this
        throw new UnsupportedOperationException();
    }
}
