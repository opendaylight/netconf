/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.BasicAuth;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.IetfHttpServerData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.TcpSupported;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.TlsSupported;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-http-server.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfHttpServerFeatureProvider implements YangFeatureProvider<IetfHttpServerData> {
    @Override
    public Class<IetfHttpServerData> boundModule() {
        return IetfHttpServerData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfHttpServerData>> supportedFeatures() {
        return Set.of(BasicAuth.VALUE, TcpSupported.VALUE, TlsSupported.VALUE);
    }
}
