/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.keystore;

import java.security.KeyPair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystore;

/**
 * An {@link AsymmetricKeyParser} capable of parsing inline definitions only.
 */
@NonNullByDefault
final class InlineAsymmetricKeyParser extends AsymmetricKeyParser {
    static final KeystoreSupport SUPPORT = new DefaultKeystoreSupport(new InlineAsymmetricKeyParser());

    private InlineAsymmetricKeyParser() {
        // hidden on purpose
    }

    @Override
    KeyPair centralKeyOf(final CentralKeystore central) throws UnsupportedConfigurationException {
        throw unsupportedConfiguration(central);
    }

    @Override
    public String toString() {
        return InlineAsymmetricKeyParser.class.getSimpleName();
    }
}
