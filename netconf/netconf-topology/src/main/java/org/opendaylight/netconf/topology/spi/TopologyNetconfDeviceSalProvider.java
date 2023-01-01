/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

/**
 * @author nite
 *
 */
public class TopologyNetconfDeviceSalProvider extends NetconfDeviceSalProvider {
    private final NetconfDeviceTopologyAdapter datastoreAdapter;

    public TopologyNetconfDeviceSalProvider(final RemoteDeviceId deviceId, final DOMMountPointService mountService,
            final DataBroker dataBroker) {
        super(deviceId, mountService);
        datastoreAdapter = new NetconfDeviceTopologyAdapter(dataBroker, deviceId);
    }

    @Override
    public void close() {
        super.close();
        datastoreAdapter.close();
    }
}
