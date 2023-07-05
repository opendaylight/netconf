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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417.ClientAuthSupported;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417.ClientAuthX509Cert;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417.IetfTlsServerData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417.ServerIdentX509Cert;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-tls-server.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfTlsServerFeatureProvider implements YangFeatureProvider<IetfTlsServerData> {
    @Override
    public Class<IetfTlsServerData> boundModule() {
        return IetfTlsServerData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfTlsServerData>> supportedFeatures() {
        // FIXME: tls-server-keepalives
        // FIXME: server-ident-raw-public-key
        // FIXME: server-ident-tls13-epsk
        // FIXME: client-auth-raw-public-key
        // FIXME: client-auth-tls13-epsk
        return Set.of(ServerIdentX509Cert.VALUE, ClientAuthSupported.VALUE, ClientAuthX509Cert.VALUE);
    }
}
