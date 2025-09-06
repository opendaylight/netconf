/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import java.security.KeyPair;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.AsymmetricKeyPairGrouping;

/**
 *
 */
final class AsymmetricKeySupport {
    private PrivateKeySupport privateKeys;
    private PublicKeySupport publicKeys;

    AsymmetricKeySupport(PrivateKeySupport privateKeys) {
        this.privateKeys = requireNonNull(privateKeys);
    }

    KeyPair resolveInline(final AsymmetricKeyPairGrouping definition) throws UnsupportedConfigurationException {

           return new KeyPair(null, privateKeys.createPrivateKey(definition));



    }

}
