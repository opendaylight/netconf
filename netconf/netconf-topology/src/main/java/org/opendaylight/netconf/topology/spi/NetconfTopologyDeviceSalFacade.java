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
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceSchema;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

/**
 * {@link NetconfDeviceSalFacade} specialization for netconf topology.
 */
public class NetconfTopologyDeviceSalFacade extends NetconfDeviceSalFacade {
    private final NetconfDeviceTopologyAdapter datastoreAdapter;

    public NetconfTopologyDeviceSalFacade(final RemoteDeviceId id, final DOMMountPointService mountPointService,
            final boolean lockDatastore, final DataBroker dataBroker) {
        super(id, mountPointService, lockDatastore);
        datastoreAdapter = new NetconfDeviceTopologyAdapter(dataBroker, id);
    }

    @Override
    public synchronized void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        super.onDeviceConnected(deviceSchema, sessionPreferences, services);
        datastoreAdapter.updateDeviceData(true, deviceSchema.capabilities());

    }

    @Override
    public synchronized void onDeviceDisconnected() {
        datastoreAdapter.updateDeviceData(false, NetconfDeviceCapabilities.empty());
        super.onDeviceDisconnected();
    }

    @Override
    public synchronized void onDeviceFailed(final Throwable throwable) {
        datastoreAdapter.setDeviceAsFailed(throwable);
        super.onDeviceFailed(throwable);
    }

    @Override
    public void close() {
        datastoreAdapter.close();
        super.close();
    }
}
