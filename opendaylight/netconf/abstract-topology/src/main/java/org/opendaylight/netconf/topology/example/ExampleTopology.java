/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import akka.actor.ActorSystem;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.NodeRoleChangeStrategy;

public class ExampleTopology {

    private static final String TOPOLOGY_NETCONF = "topology-netconf";
    private BaseTopologyManager<UserDefinedMessage> netconfNodeBaseTopologyManager;
    private final DataBroker dataBroker;
    private Peer.PeerContext<UserDefinedMessage> peerCtx;

    public ExampleTopology(final EntityOwnershipService entityOwnershipService,
                           final NetconfTopology topologyDispatcher) {
        dataBroker = topologyDispatcher.getDataBroker();
        final ActorSystem actorSystem = ActorSystem.create("netconf-cluster");

        final NodeManagerCallbackFactory<UserDefinedMessage> nodeManagerCallbackFactory = new NodeManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public NodeManagerCallback<UserDefinedMessage> create(String nodeId, String topologyId, ActorSystem actorSystem) {
                return new ExampleNodeManagerCallback(
                        nodeId, topologyId, actorSystem, topologyDispatcher, new NodeRoleChangeStrategy(entityOwnershipService, "netconf", nodeId));
            }
        };

        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = new TopologyManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public TopologyManagerCallback<UserDefinedMessage> create(ActorSystem actorSystem, DataBroker dataBroker, String topologyId, List<String> remotePaths) {
                return new ExampleTopologyManagerCallback(actorSystem, dataBroker, topologyId, remotePaths, nodeManagerCallbackFactory);
            }
        };

//        netconfNodeBaseTopologyManager = new BaseTopologyManager<>(dataBroker, TOPOLOGY_NETCONF,
//                topologyManagerCallbackFactory,
//                new SingleStateAggregator(),
//                new SalNodeWriter(dataBroker, TOPOLOGY_NETCONF),
//                new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "netconf", TOPOLOGY_NETCONF));

    }

}
