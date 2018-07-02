/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import com.google.common.util.concurrent.Futures;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfConnectorDTO;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.NetconfNode;

class TestingRemoteDeviceConnectorImpl extends RemoteDeviceConnectorImpl {

    private final NetconfDeviceCommunicator communicator;

    TestingRemoteDeviceConnectorImpl(final NetconfTopologySetup netconfTopologyDeviceSetup,
                                     final RemoteDeviceId remoteDeviceId,
                                     final NetconfDeviceCommunicator communicator) {
        super(netconfTopologyDeviceSetup, remoteDeviceId);
        this.communicator = communicator;
    }

    @Override
    public NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId, final NetconfNode node,
            final RemoteDeviceHandler<NetconfSessionPreferences> salFacade) {
        final NetconfConnectorDTO connectorDTO = new NetconfConnectorDTO(communicator, salFacade);
        doReturn(Futures.immediateCheckedFuture(null)).when(communicator).initializeRemoteConnection(any(), any());
        return connectorDTO;
    }

}
