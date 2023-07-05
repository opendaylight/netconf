/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev230417.IetfSshCommonData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev230417.SshX509Certs;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev230417.TransportParams;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Common features supported by SSH transport.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfSshCommonProvider implements YangFeatureProvider<IetfSshCommonData> {
    @Override
    public Class<IetfSshCommonData> boundModule() {
        return IetfSshCommonData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfSshCommonData>> supportedFeatures() {
        return Set.of(SshX509Certs.VALUE, TransportParams.VALUE);
    }
}
