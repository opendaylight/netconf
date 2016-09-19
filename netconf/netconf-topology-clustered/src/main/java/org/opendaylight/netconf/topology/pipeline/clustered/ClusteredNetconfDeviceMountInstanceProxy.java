/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline.clustered;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider.MountInstance;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredNetconfDeviceMountInstanceProxy implements Provider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredNetconfDeviceMountInstanceProxy.class);

    private final RemoteDeviceId id;
    private MountInstance mountInstance;

    public ClusteredNetconfDeviceMountInstanceProxy(final RemoteDeviceId deviceId) {
        this.id = deviceId;
    }

    public MountInstance getMountInstance() {
        Preconditions.checkState(mountInstance != null,
                "%s: Mount instance was not initialized by sal. Cannot get mount instance", id);
        return mountInstance;
    }

    @Override
    public void close() throws Exception {
        mountInstance.close();
    }

    @Override
    public void onSessionInitiated(final Broker.ProviderSession session) {
        LOG.debug("{}: (BI)Session with sal established {}", id, session);

        final DOMMountPointService mountService = session.getService(DOMMountPointService.class);
        if (mountService != null) {
            mountInstance = new MountInstance(mountService, id);
        }
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }
}
