/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.truststore.spi;

import java.security.PublicKey;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore._public.keys.grouping.inline.or.truststore.CentralTruststore;

/**
 * Abstract base class for resolving references to the centrally-stored trusted public keys.
 */
@NonNullByDefault
public abstract class CentralTrustedPublicKeyResolver implements TrustedPublicKeyResolver<CentralTruststore> {
    @Override
    public final Class<CentralTruststore> supportedType() {
        return CentralTruststore.class;
    }

    @Override
    public final Map<String, PublicKey> resolvePublicKeys(final CentralTruststore config)
            throws UnsupportedConfigurationException {
        final var publicKeyBagName = config.getCentralTruststoreReference();
        if (publicKeyBagName == null) {
            throw new UnsupportedOperationException("Missing central truststore reference");
        }
        return resolvePublicKeyBag(publicKeyBagName);
    }

    protected abstract Map<String, PublicKey> resolvePublicKeyBag(String name) throws UnsupportedConfigurationException;
}
