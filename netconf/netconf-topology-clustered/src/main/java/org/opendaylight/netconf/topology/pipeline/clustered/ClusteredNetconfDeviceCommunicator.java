/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline.clustered;

import java.util.ArrayList;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListenerRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class ClusteredNetconfDeviceCommunicator extends NetconfDeviceCommunicator {

    private final EntityOwnershipService ownershipService;

    private final ArrayList<NetconfClientSessionListener> netconfClientSessionListeners = new ArrayList<>();
    private EntityOwnershipListenerRegistration ownershipListenerRegistration = null;

    public ClusteredNetconfDeviceCommunicator(RemoteDeviceId id, NetconfDevice remoteDevice, EntityOwnershipService ownershipService, final int rpcMessageLimit) {
        super(id, remoteDevice, rpcMessageLimit);
        this.ownershipService = ownershipService;
    }

    @Override
    public void onMessage(NetconfClientSession session, NetconfMessage message) {
        super.onMessage(session, message);
        for(NetconfClientSessionListener listener : netconfClientSessionListeners) {
            listener.onMessage(session, message);
        }
    }

    @Override
    public void onSessionDown(NetconfClientSession session, Exception e) {
        super.onSessionDown(session, e);
        ownershipListenerRegistration.close();
        for(NetconfClientSessionListener listener : netconfClientSessionListeners) {
            listener.onSessionDown(session, e);
        }
    }

    @Override
    public void onSessionUp(NetconfClientSession session) {
        super.onSessionUp(session);
        ownershipListenerRegistration = ownershipService.registerListener("netconf-node/" + id.getName(), (ClusteredNetconfDevice) remoteDevice);
        for(NetconfClientSessionListener listener : netconfClientSessionListeners) {
            listener.onSessionUp(session);
        }
    }

    @Override
    public void onSessionTerminated(NetconfClientSession session, NetconfTerminationReason reason) {
        super.onSessionTerminated(session, reason);
        ownershipListenerRegistration.close();
        for(NetconfClientSessionListener listener : netconfClientSessionListeners) {
            listener.onSessionTerminated(session, reason);
        }
    }

    public NetconfClientSessionListenerRegistration registerNetconfClientSessionListener(NetconfClientSessionListener listener) {
        netconfClientSessionListeners.add(listener);
        return new NetconfClientSessionListenerRegistration(listener);
    }

    public class NetconfClientSessionListenerRegistration {

        private final NetconfClientSessionListener listener;

        public NetconfClientSessionListenerRegistration(NetconfClientSessionListener listener) {
            this.listener = listener;
        }

        public void close() {
            netconfClientSessionListeners.remove(listener);
        }
    }
}
