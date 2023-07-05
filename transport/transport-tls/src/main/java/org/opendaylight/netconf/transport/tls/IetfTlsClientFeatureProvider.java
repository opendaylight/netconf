/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.ClientIdentX509Cert;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.IetfTlsClientData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.ServerAuthX509Cert;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-tls-client.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfTlsClientFeatureProvider implements YangFeatureProvider<IetfTlsClientData> {
    @Override
    public Class<IetfTlsClientData> boundModule() {
        return IetfTlsClientData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfTlsClientData>> supportedFeatures() {
        // FIXME: tls-client-keepalives
        // FIXME: client-ident-raw-public-key
        // FIXME: client-ident-tls13-epsk
        // FIXME: server-auth-raw-public-key
        // FIXME: server-auth-tls13-epsk
        return Set.of(ClientIdentX509Cert.VALUE, ServerAuthX509Cert.VALUE);
    }
}
