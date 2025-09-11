/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.truststore.api;

import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

/**
 * Primary entrypoint for interpreting {@code ietf-truststore.yang}-modeled configuration.
 */
@NonNullByDefault
public interface TruststoreAccess {

    Map<String, Certificate> resolveCertificates(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
        .truststore.rev241010.inline.or.truststore.certs.grouping.InlineOrTruststore config);

    Map<String, PublicKey> resolvePublicKeys(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore
        .rev241010.inline.or.truststore._public.keys.grouping.InlineOrTruststore config)
            throws UnsupportedConfigurationException;
}
