/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp.impl;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.common.rev241010.IetfTcpCommonData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.common.rev241010.KeepalivesSupported;
import org.opendaylight.yangtools.binding.YangFeature;
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-tcp-common.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfTcpCommonFeatureProvider implements YangFeatureProvider<IetfTcpCommonData> {
    @Override
    public Class<IetfTcpCommonData> boundModule() {
        return IetfTcpCommonData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfTcpCommonData>> supportedFeatures() {
        return Set.of(KeepalivesSupported.VALUE);
    }
}
