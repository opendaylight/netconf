/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.TopologyManager;
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

        final NodeManagerCallbackFactory<UserDefinedMessage> nodeManagerCallbackFactory = new NodeManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public NodeManagerCallback<UserDefinedMessage> create(String nodeId, String topologyId) {
                return new ExampleNodeManagerCallback(
                        topologyDispatcher, new NodeRoleChangeStrategy(entityOwnershipService, "netconf", nodeId), nodeId, topologyId);
            }
        };

        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = new TopologyManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public TopologyManagerCallback<UserDefinedMessage> create(TopologyManager topologyParent) {
                return new ExampleTopologyManagerCallback(dataBroker, TOPOLOGY_NETCONF, topologyParent, nodeManagerCallbackFactory, new ExampleSingleStateAggregator());
            }
        };

//        netconfNodeBaseTopologyManager = new BaseTopologyManager<>(dataBroker, TOPOLOGY_NETCONF,
//                topologyManagerCallbackFactory,
//                new SingleStateAggregator(),
//                new SalNodeWriter(dataBroker, TOPOLOGY_NETCONF),
//                new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "netconf", TOPOLOGY_NETCONF));

    }

    public void createActors(final EntityOwnershipService entityOwnershipService,
                             final NetconfTopology topologyDispatcher) {
        final NodeManagerCallbackFactory<UserDefinedMessage> nodeManagerCallbackFactory = new NodeManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public NodeManagerCallback<UserDefinedMessage> create(String nodeId, String topologyId) {
                return new ExampleNodeManagerCallback(
                        topologyDispatcher, new NodeRoleChangeStrategy(entityOwnershipService, "netconf", nodeId), nodeId, topologyId);
            }
        };

        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = new TopologyManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public TopologyManagerCallback<UserDefinedMessage> create(TopologyManager topologyParent) {
                return new ExampleTopologyManagerCallback(dataBroker, TOPOLOGY_NETCONF, topologyParent, nodeManagerCallbackFactory, new ExampleSingleStateAggregator());
            }
        };

        final ActorSystem actorSystem = ActorSystem.create("netconf-cluster");
        final TypedActorExtension typedActorExtension = TypedActor.get(actorSystem);
//        final BaseTopologyManager netconf = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
//            @Override
//            public BaseTopologyManager create() throws Exception {
//                return new BaseTopologyManager<>(dataBroker, TOPOLOGY_NETCONF,
//                        topologyManagerCallbackFactory,
//                        new SingleStateAggregator(),
//                        new SalNodeWriter(dataBroker, TOPOLOGY_NETCONF),
//                        new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "netconf", TOPOLOGY_NETCONF));
//            }
//        }));
        TypedActor.context();

    }

}
