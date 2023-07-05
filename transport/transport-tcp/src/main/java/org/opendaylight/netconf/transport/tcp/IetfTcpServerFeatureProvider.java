/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.IetfTcpServerData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerKeepalives;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-tcp-server.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfTcpServerFeatureProvider implements YangFeatureProvider<IetfTcpServerData> {
    @Override
    public Class<IetfTcpServerData> boundModule() {
        return IetfTcpServerData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfTcpServerData>> supportedFeatures() {
        return NettyTransportSupport.keepalivesSupported() ? Set.of(TcpServerKeepalives.VALUE) : Set.of();
    }
}
