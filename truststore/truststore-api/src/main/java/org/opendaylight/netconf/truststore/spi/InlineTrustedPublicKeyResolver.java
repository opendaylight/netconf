/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.truststore.spi;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore._public.keys.grouping.inline.or.truststore.Inline;

/**
 * Abstract base class for resolving inline definitions of trusted public keys.
 */
@NonNullByDefault
public abstract class InlineTrustedPublicKeyResolver implements TrustedPublicKeyResolver<Inline> {
    @Override
    public final Class<Inline> supportedType() {
        return Inline.class;
    }

    @Override
    public final Map<String, PublicKey> resolvePublicKeys(final Inline config)
            throws UnsupportedConfigurationException {
        final var values = config.nonnullInlineDefinition().nonnullPublicKey().values();
        final var size = values.size();
        if (size == 0) {
            return Map.of();
        }

        final var tmp = HashMap.<String, PublicKey>newHashMap(size);
        for (var value : values) {
            final var name = value.requireName();
            final var pkf = value.getPublicKeyFormat();
            if (pkf == null) {
                throw new UnsupportedOperationException("Key " + name + " is missing public key format");
            }
            final var pk = value.getPublicKey();
            if (pk == null) {
                throw new UnsupportedOperationException("Key " + name + " is missing public key");
            }
            tmp.put(name, resolvePublicKey(pkf, pk));
        }

        return Map.copyOf(tmp);
    }

    protected abstract PublicKey resolvePublicKey(PublicKeyFormat format, byte[] bytes)
        throws UnsupportedConfigurationException;
}
