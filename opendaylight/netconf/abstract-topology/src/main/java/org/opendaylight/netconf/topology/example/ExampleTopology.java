/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.ConnectionAggregator.SingleConnectionAggregator;
import org.opendaylight.netconf.topology.NodeAdministratorCallback;
import org.opendaylight.netconf.topology.NodeAdministratorCallback.NodeAdministratorCallbackFactory;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.TopologyAdministratorCallback;
import org.opendaylight.netconf.topology.TopologyAdministratorCallback.TopologyAdministratorCallbackFactory;
import org.opendaylight.netconf.topology.util.BaseTopologyAdmin;

public class ExampleTopology {

    private final BaseTopologyAdmin<CustomMessage> netconfNodeBaseTopologyAdmin;
    private Peer.PeerContext<CustomMessage> peerCtx;

    private final NodeAdministratorCallbackFactory<CustomMessage> NODE_ADMIN_CALLBACK_FACTORY = new NodeAdministratorCallbackFactory<CustomMessage>() {
        @Override
        public NodeAdministratorCallback<CustomMessage> create() {
            // TODO we need to inject the topologyDispatcher into these callbacks
            return new ExampleNodeAdministratorCallback(null);
        }
    };

    private final TopologyAdministratorCallbackFactory<CustomMessage> TOPOLOGY_ADMIN_CALLBACK_FACTORY;

    public ExampleTopology(final DataBroker dataBroker) {
        TOPOLOGY_ADMIN_CALLBACK_FACTORY = new TopologyAdministratorCallbackFactory<CustomMessage>() {
            @Override
            public TopologyAdministratorCallback<CustomMessage> create() {
                return new ExampleTopologyCallback(dataBroker, "topology-netconf", NODE_ADMIN_CALLBACK_FACTORY, new SingleConnectionAggregator());
            }
        };

        netconfNodeBaseTopologyAdmin = new BaseTopologyAdmin<>(dataBroker, "topology-netconf",
                TOPOLOGY_ADMIN_CALLBACK_FACTORY.create(), new SingleConnectionAggregator());
    }

    public static final class CustomMessage {}
}
