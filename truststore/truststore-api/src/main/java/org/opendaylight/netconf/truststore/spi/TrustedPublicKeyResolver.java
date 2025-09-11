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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore._public.keys.grouping.InlineOrTruststore;
import org.opendaylight.yangtools.binding.DataObject;

/**
 * SPI-level contract for plugging into process of resolving trusted public keys.
 */
public interface TrustedPublicKeyResolver<T extends InlineOrTruststore & DataObject> {
    @NonNullByDefault
    Class<T> supportedType();

    @NonNullByDefault
    Map<String, PublicKey> resolvePublicKeys(T config) throws UnsupportedConfigurationException;
}
