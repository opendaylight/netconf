/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.udp;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.udp.client.rev241004.IetfUdpClientData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.udp.client.rev241004.LocalBindingSupported;
import org.opendaylight.yangtools.binding.YangFeature;
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-udp-client.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfUdpClientFeatureProvider implements YangFeatureProvider<IetfUdpClientData> {
    @Override
    public Class<IetfUdpClientData> boundModule() {
        return IetfUdpClientData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfUdpClientData>> supportedFeatures() {
        return Set.of(LocalBindingSupported.VALUE);
    }
}
