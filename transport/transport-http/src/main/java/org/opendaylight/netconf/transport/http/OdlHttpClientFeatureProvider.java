/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.OdlHttpClientData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.QuicSupported;
import org.opendaylight.yangtools.binding.YangFeature;
import org.opendaylight.yangtools.binding.meta.RootMeta;
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

/**
 * Features supported from {@code odl-http-client.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class OdlHttpClientFeatureProvider implements YangFeatureProvider<OdlHttpClientData> {
    @Override
    public RootMeta<OdlHttpClientData> boundModule() {
        return OdlHttpClientData.META;
    }

    @Override
    public Set<? extends YangFeature<?, OdlHttpClientData>> supportedFeatures() {
        return Set.of(QuicSupported.VALUE);
    }
}
