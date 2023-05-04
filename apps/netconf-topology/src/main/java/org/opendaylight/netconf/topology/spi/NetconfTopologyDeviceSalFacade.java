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
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceSalFacade;

/**
 * {@link NetconfDeviceSalFacade} specialization for netconf topology.
 */
public class NetconfTopologyDeviceSalFacade extends NetconfDeviceSalFacade {
    private final NetconfDeviceTopologyAdapter datastoreAdapter;

    public NetconfTopologyDeviceSalFacade(final RemoteDeviceId id, final DOMMountPointService mountPointService,
            final boolean lockDatastore, final DataBroker dataBroker) {
        super(id, mountPointService, NetconfNodeUtils.defaultTopologyMountPath(id), lockDatastore);
        datastoreAdapter = new NetconfDeviceTopologyAdapter(dataBroker, NetconfNodeUtils.DEFAULT_TOPOLOGY_IID, id);
    }

    @Override
    public synchronized void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        super.onDeviceConnected(deviceSchema, sessionPreferences, services);
        datastoreAdapter.updateDeviceData(true, deviceSchema.capabilities(), sessionPreferences.sessionId());
    }

    @Override
    public synchronized void onDeviceDisconnected() {
        datastoreAdapter.updateDeviceData(false, NetconfDeviceCapabilities.empty(), null);
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
