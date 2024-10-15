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
import org.opendaylight.netconf.transport.spi.NettyTransportSupport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.IetfTcpServerData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.TcpServerKeepalives;
import org.opendaylight.yangtools.binding.YangFeature;
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

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
        return NettyTransportSupport.tcpKeepaliveOptions() != null ? Set.of(TcpServerKeepalives.VALUE) : Set.of();
    }
}
