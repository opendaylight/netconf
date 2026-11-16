/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.keystore;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.security.KeyPair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.keystore.api.KeystoreAccess;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystore;

/**
 * An {@link AsymmetricKeyParser} backed by a {@link KeystoreAccess} instance.
 */
@NonNullByDefault
final class KeystoreAsymmetricKeyParser extends AsymmetricKeyParser {
    private final KeystoreAccess keystore;

    KeystoreAsymmetricKeyParser(final KeystoreAccess keystore) {
        this.keystore = requireNonNull(keystore);
    }

    @Override
    protected KeyPair centralKeyOf(final CentralKeystore input) throws UnsupportedConfigurationException {
        final var keyName = input.getCentralKeystoreReference();
        if (keyName == null) {
            throw new UnsupportedConfigurationException("Missing central keystore reference in " + input);
        }
        final var key = keystore.lookupAsymmetric(keyName);
        if (key == null) {
            throw new UnsupportedConfigurationException("Cannot resolve key " + keyName);
        }
        return key;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("keystore", keystore).toString();
    }
}
