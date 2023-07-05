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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.IetfTcpClientData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.LocalBindingSupported;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientKeepalives;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-tcp-client.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfTcpClientFeatureProvider implements YangFeatureProvider<IetfTcpClientData> {
    @Override
    public Class<IetfTcpClientData> boundModule() {
        return IetfTcpClientData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfTcpClientData>> supportedFeatures() {
        return NettyTransportSupport.keepalivesSupported()
            ? Set.of(LocalBindingSupported.VALUE, TcpClientKeepalives.VALUE) : Set.of(LocalBindingSupported.VALUE);
    }
}
