/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.mockito.Mockito.doReturn;

import java.util.List;
import org.opendaylight.netconf.nativ.netconf.communicator.NativeNetconfDeviceCommunicator;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.spi.NetconfConnectorDTO;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;

class TestingRemoteDeviceConnectorImpl extends RemoteDeviceConnectorImpl {

    private final NativeNetconfDeviceCommunicator communicator;

    TestingRemoteDeviceConnectorImpl(final NetconfTopologySetup netconfTopologyDeviceSetup,
                                     final RemoteDeviceId remoteDeviceId,
            final NativeNetconfDeviceCommunicator communicator,
                                     final DeviceActionFactory deviceActionFactory) {
        super(netconfTopologyDeviceSetup, remoteDeviceId, deviceActionFactory);

        this.communicator = communicator;
    }

    @Override
    public NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId, final NetconfNode node,
            final RemoteDeviceHandler<NetconfSessionPreferences> salFacade) {
        final NetconfConnectorDTO connectorDTO = new NetconfConnectorDTO(communicator, salFacade, List.of());
        doReturn(FluentFutures.immediateNullFluentFuture()).when(communicator).initializeRemoteConnection();
        return connectorDTO;
    }
}
