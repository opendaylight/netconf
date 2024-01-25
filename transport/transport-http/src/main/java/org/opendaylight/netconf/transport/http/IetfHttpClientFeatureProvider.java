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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev230417.BasicAuth;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev230417.IetfHttpClientData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev230417.TcpSupported;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev230417.TlsSupported;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-http-client.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfHttpClientFeatureProvider implements YangFeatureProvider<IetfHttpClientData> {
    @Override
    public Class<IetfHttpClientData> boundModule() {
        return IetfHttpClientData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfHttpClientData>> supportedFeatures() {
        return Set.of(BasicAuth.VALUE, TcpSupported.VALUE, TlsSupported.VALUE);
    }
}
