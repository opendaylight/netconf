/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import static org.junit.Assert.assertTrue;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import akka.actor.TypedProps;
import akka.japi.Creator;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.StateAggregator.SingleStateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.NodeRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.netconf.topology.util.TopologyRoleChangeStrategy;

public class ActorTest {

    private static final String TOPOLOGY_NETCONF = "topology-netconf";

    @Mock
    private NetconfTopology topologyDispatcher;

    @Mock
    private EntityOwnershipService entityOwnershipService;

    @Mock
    private DataBroker dataBroker;

    @Test
    public void actorTest() {

        final NodeManagerCallbackFactory<UserDefinedMessage> nodeManagerCallbackFactory = new NodeManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public NodeManagerCallback<UserDefinedMessage> create(String nodeId, String topologyId) {
                return new ExampleNodeManagerCallback(
                        topologyDispatcher,  new NodeRoleChangeStrategy(entityOwnershipService, "netconf", nodeId), String nodeId, String topologyId);
            }
        };

        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = new TopologyManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public TopologyManagerCallback<UserDefinedMessage> create(TopologyManager topologyParent) {
                return new ExampleTopologyManagerCallback(dataBroker, TOPOLOGY_NETCONF, topologyParent, nodeManagerCallbackFactory, new SingleStateAggregator());
            }
        };

        final ActorSystem actorSystem = ActorSystem.create("netconf-test-cluster");
        final TypedActorExtension typedActorExtension = TypedActor.get(actorSystem);
        TopologyManager netconf = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
            @Override
            public BaseTopologyManager create() throws Exception {
                return new BaseTopologyManager<>(dataBroker, TOPOLOGY_NETCONF,
                        topologyManagerCallbackFactory,
                        new SingleStateAggregator(),
                        new SalNodeWriter(dataBroker, TOPOLOGY_NETCONF),
                        new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "netconf", TOPOLOGY_NETCONF));
            }
        }), "topology1");
        typedActorExtension.getActorRefFor(netconf);

        ActorRef refFromTypedActor = typedActorExtension.getActorRefFor(netconf);
        ActorRef refFromPath = actorSystem.actorFor("/user/topology1");

        TopologyManager typedActorFromPath = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), refFromPath);
        assertTrue(refFromTypedActor == refFromPath);

    }
}
