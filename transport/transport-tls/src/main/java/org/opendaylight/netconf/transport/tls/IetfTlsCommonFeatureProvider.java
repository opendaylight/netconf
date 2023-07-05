/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev230417.HelloParams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev230417.IetfTlsCommonData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev230417.Tls12$F;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev230417.Tls12$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev230417.Tls13$F;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev230417.Tls13$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev230417.TlsVersionBase;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-tls-common.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfTlsCommonFeatureProvider implements YangFeatureProvider<IetfTlsCommonData> {
    private static final Map<TlsVersionBase, String> TLS_VERSIONS =
        Map.of(Tls12$I.VALUE, "TLSv1.2", Tls13$I.VALUE, "TLSv1.3");

    @Override
    public Class<IetfTlsCommonData> boundModule() {
        return IetfTlsCommonData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfTlsCommonData>> supportedFeatures() {
        // Note: do not advertize TLS versions not present in TLS_VERSIONS!
        return Set.of(HelloParams.VALUE, Tls12$F.VALUE, Tls13$F.VALUE);
    }

    static @Nullable String algorithmNameOf(final TlsVersionBase version) {
        return TLS_VERSIONS.get(version);
    }
}
