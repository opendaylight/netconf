/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.security.PublicKey;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PublicKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;

/**
 *
 */
final class PublicKeySupport {
    private final Map<PublicKeyFormat, Function<byte[], PublicKey>> supports = Map.of(
        SshPublicKeyFormat.VALUE, null,
        SubjectPublicKeyInfoFormat.VALUE, null);

    // Note: unchecked exceptions for ease of use
    PublicKey createPrivateKey(final PublicKeyGrouping definition)
            throws NoSuchElementException, UnsupportedConfigurationException {
        final var keyFormat = definition.requirePublicKeyFormat();
        final var keyFactory = supports.get(keyFormat);
        if (keyFactory == null) {
            throw new UnsupportedConfigurationException("Unsupported key format " + keyFormat);
        }
        return keyFactory.apply(definition.requirePublicKey());
    }
}
