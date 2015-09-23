/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.StateAggregator.SingleStateAggregator;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.NodeElectionStrategy;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.netconf.topology.util.TopologyElectionStrategy;

public class ExampleTopology {

    private static final String TOPOLOGY_NETCONF = "topology-netconf";
    private final BaseTopologyManager<UserDefinedMessage> netconfNodeBaseTopologyManager;
    private Peer.PeerContext<UserDefinedMessage> peerCtx;

    public ExampleTopology(final DataBroker dataBroker,
                           final EntityOwnershipService entityOwnershipService,
                           final NetconfTopology topologyDispatcher) {
        final NodeManagerCallbackFactory<UserDefinedMessage> nodeManagerCallbackFactory = new NodeManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public NodeManagerCallback<UserDefinedMessage> create(NodeManager electionDelegate, String nodeId) {
                return new ExampleNodeManagerCallback(
                        topologyDispatcher, electionDelegate, new NodeElectionStrategy(entityOwnershipService, "netconf", nodeId));
            }
        };

        //User needs to implement own StateAggergator
        final ExampleTopologyManagerCallback exampleTopologyManagerCallback = new ExampleTopologyManagerCallback(dataBroker,
                "topology-netconf", nodeManagerCallbackFactory, new SingleStateAggregator());


        netconfNodeBaseTopologyManager = new BaseTopologyManager<>(dataBroker,TOPOLOGY_NETCONF,
                exampleTopologyManagerCallback,
                new SingleStateAggregator(),
                new SalNodeWriter(dataBroker, TOPOLOGY_NETCONF),
                new TopologyElectionStrategy(dataBroker, entityOwnershipService, "netconf", TOPOLOGY_NETCONF));

    }

}
