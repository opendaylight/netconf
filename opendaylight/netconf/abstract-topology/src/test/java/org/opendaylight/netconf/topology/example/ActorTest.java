/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import static org.junit.Assert.assertTrue;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import akka.actor.TypedProps;
import akka.japi.Creator;
import akka.util.Timeout;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.typesafe.config.ConfigFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.NodeRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.TopologyRoleChangeStrategy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeFields;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

public class ActorTest {

    private static final Logger LOG = LoggerFactory.getLogger(ActorTest.class);

    private static final String TOPOLOGY_NETCONF = "TopologyNetconf";

    @Mock
    private NetconfTopology topologyDispatcher;

    @Mock
    private EntityOwnershipService entityOwnershipService;

    @Mock
    private DataBroker dataBroker;

    @Test
    public void actorTest() throws Exception {

//        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = createRealTopoCallbackFactory();
        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = createTestingTopoCallbackFactory();


        // load from config
        final List<String> paths = Lists.newArrayList("akka.tcp://NetconfNode@127.0.0.1:2553/user/topology",
                "akka.tcp://NetconfNode@127.0.0.1:2554/user/topology");

        final ActorSystem actorSystem = ActorSystem.create("NetconfNode", ConfigFactory.load("netconf-node1"));
        final TypedActorExtension typedActorExtension = TypedActor.get(actorSystem);
        TopologyManager netconf = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
            @Override
            public BaseTopologyManager create() throws Exception {
                return new BaseTopologyManager<>(actorSystem,
                        paths,
                        dataBroker,
                        TOPOLOGY_NETCONF,
                        topologyManagerCallbackFactory,
                        new OnlySuccessStateAggregator(),
                        new LoggingSalNodeWriter(),
                        new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "netconf", TOPOLOGY_NETCONF),
                        true);
            }
        }), "topology");
        typedActorExtension.getActorRefFor(netconf);

        ActorRef refFromTypedActor = typedActorExtension.getActorRefFor(netconf);
        ActorRef refFromPath = null;
        try {
            refFromPath = Await.result(actorSystem.actorSelection("/user/topology").resolveOne(new Timeout(5l, TimeUnit.SECONDS)), Duration.create(5l, TimeUnit.SECONDS));
        } catch (Exception e) {
            LOG.error("Actor get from selection failed", e);
        }

        TopologyManager typedActorFromPath = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), refFromPath);
        assertTrue(refFromTypedActor == refFromPath);

        System.out.println("original actor : " + netconf.getId());
        System.out.println("actor from path : " + typedActorFromPath.getId());

        createNode1();
        createNode2();

        try {
            refFromPath = Await.result(actorSystem.actorSelection("akka.tcp://NetconfNode@127.0.0.1:2553/user/topology").resolveOne(new Timeout(5l, TimeUnit.SECONDS)), Duration.create(5l, TimeUnit.SECONDS));
        } catch (Exception e) {
            LOG.error("Actor get from selection failed", e);
        }

        typedActorFromPath = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), refFromPath);
        System.out.println("actor from path : " + typedActorFromPath.getId());

        try {
            refFromPath = Await.result(actorSystem.actorSelection("akka.tcp://NetconfNode@127.0.0.1:2554/user/topology").resolveOne(new Timeout(5l, TimeUnit.SECONDS)), Duration.create(5l, TimeUnit.SECONDS));
        } catch (Exception e) {
            LOG.error("Actor get from selection failed", e);
        }

        typedActorFromPath = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), refFromPath);
        System.out.println("actor from path : " + typedActorFromPath.getId());

        netconf.getTopologyId();

        Node testingNode = new NodeBuilder()
                .setNodeId(new NodeId("testing-node"))
                .addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder()
                                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                .setPort(new PortNumber(2555))
                                .build())
                .build();
        ListenableFuture<Node> nodeListenableFuture = netconf.nodeCreated(new NodeId("testing-node"), testingNode);
        Node node = nodeListenableFuture.get();
        node.getKey();
    }

    public void createNode1() {
//        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = createRealTopoCallbackFactory();
        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = createTestingTopoCallbackFactory();

        // load from config
        final List<String> paths = Lists.newArrayList("akka.tcp://NetconfNode@127.0.0.1:2552/user/topology",
                "akka.tcp://NetconfNode@127.0.0.1:2554/user/topology");
        final ActorSystem actorSystem = ActorSystem.create("NetconfNode", ConfigFactory.load("netconf-node2"));
        final TypedActorExtension typedActorExtension = TypedActor.get(actorSystem);
        TopologyManager netconf = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
            @Override
            public BaseTopologyManager create() throws Exception {
                return new BaseTopologyManager<>(actorSystem,
                        paths,
                        dataBroker,
                        TOPOLOGY_NETCONF,
                        topologyManagerCallbackFactory,
                        new OnlySuccessStateAggregator(),
                        new LoggingSalNodeWriter(),
                        new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "netconf", TOPOLOGY_NETCONF));
            }
        }), "topology");

        System.out.println("original actor : " + netconf.getId());
    }

    public void createNode2() {
//        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = createRealTopoCallbackFactory();
        final TopologyManagerCallbackFactory<UserDefinedMessage> topologyManagerCallbackFactory = createTestingTopoCallbackFactory();

        // load from config
        final List<String> paths = Lists.newArrayList("akka.tcp://NetconfNode@127.0.0.1:2552/user/topology",
                "akka.tcp://NetconfNode@127.0.0.1:2553/user/topology");
        final ActorSystem actorSystem = ActorSystem.create("NetconfNode", ConfigFactory.load("netconf-node3"));
        final TypedActorExtension typedActorExtension = TypedActor.get(actorSystem);
        TopologyManager netconf = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
            @Override
            public BaseTopologyManager create() throws Exception {
                return new BaseTopologyManager<>(actorSystem,
                        paths,
                        dataBroker,
                        TOPOLOGY_NETCONF,
                        topologyManagerCallbackFactory,
                        new OnlySuccessStateAggregator(),
                        new LoggingSalNodeWriter(),
                        new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "netconf", TOPOLOGY_NETCONF));
            }
        }), "topology");

        System.out.println("original actor : " + netconf.getId());
    }

    private TopologyManagerCallbackFactory<UserDefinedMessage> createRealTopoCallbackFactory() {
        final NodeManagerCallbackFactory<UserDefinedMessage> nodeManagerCallbackFactory = new NodeManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public NodeManagerCallback<UserDefinedMessage> create(String nodeId, String topologyId) {
                return new ExampleNodeManagerCallback(
                        topologyDispatcher,  new NodeRoleChangeStrategy(entityOwnershipService, "netconf", nodeId), nodeId, topologyId);
            }
        };

        return new TopologyManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public TopologyManagerCallback<UserDefinedMessage> create(TopologyManager topologyParent) {
                return new ExampleTopologyManagerCallback(dataBroker, TOPOLOGY_NETCONF, topologyParent, nodeManagerCallbackFactory, new ExampleSingleStateAggregator());
            }
        };
    }

    private TopologyManagerCallbackFactory<UserDefinedMessage> createTestingTopoCallbackFactory() {
        return new TopologyManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public TopologyManagerCallback<UserDefinedMessage> create(TopologyManager topologyParent) {
                return new TestingTopologyManagerCallback();
            }
        };
    }

    public static class TestingTopologyManagerCallback implements TopologyManagerCallback<UserDefinedMessage>{

        public TestingTopologyManagerCallback() {

        }

        @Override
        public ListenableFuture<Node> nodeCreated(ActorContext context, NodeId nodeId, Node node) {

            return Futures.immediateFuture(new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connecting)
                                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                    .setPort(new PortNumber(2555))
                                    .build())
                    .build());
        }

        @Override
        public ListenableFuture<Node> nodeUpdated(NodeId nodeId, Node node) {
            return null;
        }

        @Override
        public ListenableFuture<Void> nodeDeleted(NodeId nodeId) {
            return null;
        }

        @Override
        public boolean isMaster() {
            return false;
        }

        @Override
        public Iterable<TopologyManager<UserDefinedMessage>> getPeers() {
            return null;
        }

        @Override
        public void onRoleChanged(RoleChangeDTO roleChangeDTO) {

        }
    }

    public class OnlySuccessStateAggregator implements StateAggregator {

        @Override
        public ListenableFuture<Node> combineCreateAttempts(List<ListenableFuture<Node>> stateFutures) {
            final SettableFuture<Node> future = SettableFuture.create();
            final ListenableFuture<List<Node>> allAsList = Futures.allAsList(stateFutures);
            Futures.addCallback(allAsList, new FutureCallback<List<Node>>() {
                @Override
                public void onSuccess(List<Node> result) {
                    for (int i = 0; i < result.size() - 1; i++) {
                        if (!result.get(i).equals(result.get(i + 1))) {
                            future.setException(new IllegalStateException("Futures have different result"));
                        }
                    }
                    future.set(result.get(0));
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("One of the combined futures failed {}", t);
                    future.setException(t);
                }
            }, TypedActor.context().dispatcher());

            return future;
        }

        @Override
        public ListenableFuture<Node> combineUpdateAttempts(List<ListenableFuture<Node>> stateFutures) {
            return null;
        }

        @Override
        public ListenableFuture<Void> combineDeleteAttempts(List<ListenableFuture<Void>> stateFutures) {
            return null;
        }
    }
}
