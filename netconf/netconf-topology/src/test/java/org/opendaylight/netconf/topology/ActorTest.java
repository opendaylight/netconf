/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import static com.jayway.awaitility.Awaitility.await;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import akka.actor.TypedProps;
import akka.japi.Creator;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.typesafe.config.ConfigFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javassist.ClassPool;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.example.ExampleNodeManagerCallback;
import org.opendaylight.netconf.topology.example.ExampleTopologyManagerCallback;
import org.opendaylight.netconf.topology.example.LoggingSalNodeWriter;
import org.opendaylight.netconf.topology.impl.NetconfNodeOperationalDataAggregator;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.NoopRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.TopologyRoleChangeStrategy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.UnavailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatus.Status;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.sal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.yangtools.sal.binding.generator.util.JavassistUtils;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActorTest {

    private static final Logger LOG = LoggerFactory.getLogger(ActorTest.class);

    private static final String TOPOLOGY_NETCONF = "TopologyNetconf";

    @Mock
    private EntityOwnershipService entityOwnershipService;

    @Mock
    private DataBroker dataBroker;

    @Mock
    private ReadOnlyTransaction mockedReadOnlyTx;

    private static final BindingNormalizedNodeCodecRegistry CODEC_REGISTRY;

    static {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(Collections.singletonList($YangModuleInfoImpl.getInstance()));
        final Optional<SchemaContext> schemaContextOptional = moduleInfoBackedContext.tryToCreateSchemaContext();
        Preconditions.checkState(schemaContextOptional.isPresent());
        final SchemaContext topologySchemaCtx = schemaContextOptional.get();

        final JavassistUtils javassist = JavassistUtils.forClassPool(ClassPool.getDefault());
        CODEC_REGISTRY = new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(javassist));
        CODEC_REGISTRY.onBindingRuntimeContextUpdated(BindingRuntimeContext.create(moduleInfoBackedContext, topologySchemaCtx));
    }

    private static final String PATH_MASTER = "akka.tcp://NetconfNode@127.0.0.1:2552/user/TopologyNetconf";
    private static final String PATH_SLAVE1 = "akka.tcp://NetconfNode@127.0.0.1:2553/user/TopologyNetconf";
    private static final String PATH_SLAVE2 = "akka.tcp://NetconfNode@127.0.0.1:2554/user/TopologyNetconf";

    private static final List<String> PATHS_MASTER = Lists.newArrayList(PATH_SLAVE1, PATH_SLAVE2);
    private static final List<String> PATHS_SLAVE1 = Lists.newArrayList(PATH_MASTER, PATH_SLAVE2);
    private static final List<String> PATHS_SLAVE2 = Lists.newArrayList(PATH_MASTER, PATH_SLAVE1);

    private static final ActorSystem ACTOR_SYSTEM = ActorSystem.create("NetconfNode", ConfigFactory.load("netconf-node1"));
    private static final ActorSystem ACTOR_SYSTEM_SLAVE1 = ActorSystem.create("NetconfNode", ConfigFactory.load("netconf-node2"));
    private static final ActorSystem ACTOR_SYSTEM_SLAVE2 = ActorSystem.create("NetconfNode", ConfigFactory.load("netconf-node3"));

    private static final ExecutorService callbackExecutor = Executors.newFixedThreadPool(8);

    private TopologyManager master = null;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final SettableFuture<Optional<Topology>> settableFuture = SettableFuture.create();
        final CheckedFuture<Optional<Topology>, ReadFailedException> checkedFuture = Futures.makeChecked(settableFuture, new Function<Exception, ReadFailedException>() {
            @Nullable
            @Override
            public ReadFailedException apply(Exception input) {
                return new ReadFailedException("Dummy future should never return this");
            }
        });
        settableFuture.set(Optional.<Topology>absent());
        when(mockedReadOnlyTx.read(any(LogicalDatastoreType.class), any(InstanceIdentifier.class))).thenReturn(checkedFuture);
        when(dataBroker.registerDataChangeListener(
                any(LogicalDatastoreType.class),
                any(InstanceIdentifier.class),
                any(DataChangeListener.class),
                any(DataChangeScope.class))).thenReturn(null);
        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockedReadOnlyTx);
    }

    private void setMaster(final TopologyManager manager) {

    }

    @Test
    public void testRealActors() throws Exception {

        EntityOwnershipService topoOwnership = new TestingEntityOwnershipService();
        // load from config
        final TopologyManager master = createManagerWithOwnership(ACTOR_SYSTEM, TOPOLOGY_NETCONF, true, createRealTopoTestingNodeCallbackFactory(), new TopologyRoleChangeStrategy(dataBroker, topoOwnership, TOPOLOGY_NETCONF, "topology-manager"));
        Thread.sleep(1000);
        final TopologyManager slave1 = createManagerWithOwnership(ACTOR_SYSTEM_SLAVE1, TOPOLOGY_NETCONF, false, createRealTopoTestingNodeCallbackFactory(), new TopologyRoleChangeStrategy(dataBroker, topoOwnership, TOPOLOGY_NETCONF, "topology-manager"));
        final TopologyManager slave2 = createManagerWithOwnership(ACTOR_SYSTEM_SLAVE2, TOPOLOGY_NETCONF, false, createRealTopoTestingNodeCallbackFactory(), new TopologyRoleChangeStrategy(dataBroker, topoOwnership, TOPOLOGY_NETCONF, "topology-manager"));

        await().atMost(30L, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return master.hasAllPeersUp();
            }
        });

        final List<ListenableFuture<Node>> futures = new ArrayList<>();
        for (int i = 0; i <= 1; i++) {
            final String nodeid = "testing-node" + i;
            final Node testingNode = new NodeBuilder()
                    .setNodeId(new NodeId(nodeid))
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                    .setPort(new PortNumber(10000 + i))
                                    .build())
                    .build();
            final ListenableFuture<Node> nodeListenableFuture = master.onNodeCreated(new NodeId(nodeid), testingNode);
            futures.add(nodeListenableFuture);
            Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
                @Override
                public void onSuccess(Node result) {
                    LOG.warn("Node {} created succesfully on all nodes", result.getNodeId().getValue());
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.warn("Node creation failed. ", t);
                }
            });
        }

        for (int i = 0; i <= 1; i++) {
            final String nodeid = "testing-node" + i;
            final Node testingNode = new NodeBuilder()
                    .setNodeId(new NodeId(nodeid))
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                    .setPort(new PortNumber(10000 + i))
                                    .build())
                    .build();
            final ListenableFuture<Node> nodeListenableFuture = master.onNodeUpdated(new NodeId(nodeid), testingNode);
            futures.add(nodeListenableFuture);
            Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
                @Override
                public void onSuccess(Node result) {
                    LOG.warn("Node {} updated succesfully on all nodes", result.getNodeId().getValue());
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.warn("Node update failed. ", t);
                }
            });
        }
        LOG.debug("Waiting for updates to finish");
        Futures.allAsList(futures).get();


        final List<ListenableFuture<Void>> deleteFutures = new ArrayList<>();
        for (int i = 0; i <= 1; i++) {
            final String nodeid = "testing-node" + i;
            final ListenableFuture<Void> nodeListenableFuture = master.onNodeDeleted(new NodeId(nodeid));
            deleteFutures.add(nodeListenableFuture);
            Futures.addCallback(nodeListenableFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    LOG.warn("Node {} succesfully deleted on all nodes", nodeid);
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.warn("Node delete failed. ", t);
                }
            });

        }
        LOG.warn("All tasks submitted");
        Futures.allAsList(futures).get();
        Futures.allAsList(deleteFutures).get();

        TypedActor.get(ACTOR_SYSTEM).stop(master);
        TypedActor.get(ACTOR_SYSTEM_SLAVE1).stop(slave1);
        TypedActor.get(ACTOR_SYSTEM_SLAVE2).stop(slave2);

    }

    // TODO seems like stopping actors is not enough to create an actor with same name, split this into multiple classes?
    @Ignore
    @Test
    public void testWithDummyOwnershipService() throws Exception {

        final TestingEntityOwnershipService ownershipService = new TestingEntityOwnershipService();
        // load from config
        final TopologyManager master = createNoopRoleChangeNode(ACTOR_SYSTEM, TOPOLOGY_NETCONF, true, createRealTopoCallbackFactory(ownershipService));
        final TopologyManager slave1 = createNoopRoleChangeNode(ACTOR_SYSTEM_SLAVE1, TOPOLOGY_NETCONF, false, createRealTopoCallbackFactory(ownershipService));
        final TopologyManager slave2 = createNoopRoleChangeNode(ACTOR_SYSTEM_SLAVE2, TOPOLOGY_NETCONF, false, createRealTopoCallbackFactory(ownershipService));

        await().atMost(10L, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return master.hasAllPeersUp();
            }
        });

        final List<ListenableFuture<Node>> futures = new ArrayList<>();
        for (int i = 0; i <= 0; i++) {
            final String nodeid = "testing-node" + i;
            final Node testingNode = new NodeBuilder()
                    .setNodeId(new NodeId(nodeid))
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                    .setPort(new PortNumber(10000 + i))
                                    .build())
                    .build();
            final ListenableFuture<Node> nodeListenableFuture = master.onNodeCreated(new NodeId(nodeid), testingNode);
            futures.add(nodeListenableFuture);
            Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
                @Override
                public void onSuccess(Node result) {
                    LOG.warn("Node {} created succesfully on all nodes", result.getNodeId().getValue());
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.warn("Node creation failed. ", t);
                }
            });
        }

        Futures.allAsList(futures).get();
        ownershipService.distributeOwnership();

        Thread.sleep(30000);
        TypedActor.get(ACTOR_SYSTEM).stop(master);
        TypedActor.get(ACTOR_SYSTEM_SLAVE1).stop(slave1);
        TypedActor.get(ACTOR_SYSTEM_SLAVE2).stop(slave2);
    }

    private TopologyManager createNoopRoleChangeNode(final ActorSystem actorSystem, final String topologyId, final boolean isMaster,
                                                     final TopologyManagerCallbackFactory topologyManagerCallbackFactory) {

        final TypedActorExtension typedActorExtension = TypedActor.get(actorSystem);
        return typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
            @Override
            public BaseTopologyManager create() throws Exception {
                return new BaseTopologyManager(actorSystem,
                        CODEC_REGISTRY,
                        dataBroker,
                        topologyId,
                        topologyManagerCallbackFactory,
                        new TestingSuccesfulStateAggregator(),
                        new LoggingSalNodeWriter(),
                        new NoopRoleChangeStrategy(),
                        isMaster);
            }
        }), TOPOLOGY_NETCONF);
    }

    private TopologyManager createManagerWithOwnership(final ActorSystem actorSystem, final String topologyId, final boolean isMaster,
                                                       final TopologyManagerCallbackFactory topologyManagerCallbackFactory, final RoleChangeStrategy roleChangeStrategy) {
        final TypedActorExtension typedActorExtension = TypedActor.get(actorSystem);
        return typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
            @Override
            public BaseTopologyManager create() throws Exception {
                return new BaseTopologyManager(actorSystem,
                        CODEC_REGISTRY,
                        dataBroker,
                        topologyId,
                        topologyManagerCallbackFactory,
                        new NetconfNodeOperationalDataAggregator(),
                        new LoggingSalNodeWriter(),
                        roleChangeStrategy,
                        isMaster);
            }
        }), TOPOLOGY_NETCONF);
    }

    private TopologyManagerCallbackFactory createRealTopoTestingNodeCallbackFactory() {
        final NodeManagerCallbackFactory nodeManagerCallbackFactory = new NodeManagerCallbackFactory() {
            @Override
            public NodeManagerCallback create(String nodeId, String topologyId, ActorSystem actorSystem) {
                return new LoggingNodeManagerCallback();
            }
        };

        return new TopologyManagerCallbackFactory() {
            @Override
            public TopologyManagerCallback create(ActorSystem actorSystem, String topologyId) {
                return new ExampleTopologyManagerCallback(actorSystem, dataBroker, topologyId, nodeManagerCallbackFactory, new LoggingSalNodeWriter());
            }
        };
    }

    private TopologyManagerCallbackFactory createRealTopoCallbackFactory(final EntityOwnershipService entityOwnershipService) {
        final NodeManagerCallbackFactory nodeManagerCallbackFactory = new NodeManagerCallbackFactory() {
            @Override
            public NodeManagerCallback create(String nodeId, String topologyId, ActorSystem actorSystem) {
                return new ExampleNodeManagerCallback();
            }
        };

        return new TopologyManagerCallbackFactory() {
            @Override
            public TopologyManagerCallback create(ActorSystem actorSystem, String topologyId) {
                return new ExampleTopologyManagerCallback(actorSystem, dataBroker, topologyId, nodeManagerCallbackFactory);
            }
        };
    }

    private TopologyManagerCallbackFactory createTestingTopoCallbackFactory() {
        return new TopologyManagerCallbackFactory() {
            @Override
            public TopologyManagerCallback create(ActorSystem actorSystem, String topologyId) {
                return new TestingTopologyManagerCallback();
            }
        };
    }

    public static class LoggingNodeManagerCallback implements NodeManagerCallback {

        @Nonnull
        @Override
        public Node getInitialState(@Nonnull NodeId nodeId, @Nonnull Node configNode) {
            final NetconfNode netconfNode = configNode.getAugmentation(NetconfNode.class);
            return new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setHost(netconfNode.getHost())
                                    .setPort(netconfNode.getPort())
                                    .setConnectionStatus(ConnectionStatus.Connecting)
                                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                    .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                    .setClusteredConnectionStatus(
                                            new ClusteredConnectionStatusBuilder()
                                                    .setNodeStatus(
                                                            Lists.newArrayList(
                                                                    new NodeStatusBuilder()
                                                                            .setNode("testing-node")
                                                                            .setStatus(Status.Unavailable)
                                                                            .build()))
                                                    .build())
                                    .build())
                    .build();
        }

        @Nonnull
        @Override
        public Node getFailedState(@Nonnull NodeId nodeId, @Nonnull Node configNode) {
            final NetconfNode netconfNode = configNode.getAugmentation(NetconfNode.class);
            return new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setHost(netconfNode.getHost())
                                    .setPort(netconfNode.getPort())
                                    .setConnectionStatus(ConnectionStatus.UnableToConnect)
                                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                    .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                    .setClusteredConnectionStatus(
                                            new ClusteredConnectionStatusBuilder()
                                                    .setNodeStatus(
                                                            Collections.singletonList(
                                                                    new NodeStatusBuilder()
                                                                            .setNode("testing-node")
                                                                            .setStatus(Status.Failed)
                                                                            .build()))
                                                    .build())
                                    .build())
                    .build();
        }

        @Nonnull
        @Override
        public ListenableFuture<Node> onNodeCreated(@Nonnull NodeId nodeId, @Nonnull Node configNode) {
            LOG.debug("Creating node {} with config {}", nodeId, configNode);
            final NetconfNode augmentation = configNode.getAugmentation(NetconfNode.class);
            return Futures.immediateFuture(new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setConnectionStatus(ConnectionStatus.Connected)
                                    .setHost(augmentation.getHost())
                                    .setPort(augmentation.getPort())
                                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                    .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                    .setClusteredConnectionStatus(
                                            new ClusteredConnectionStatusBuilder()
                                                    .setNodeStatus(
                                                            Collections.singletonList(
                                                                    new NodeStatusBuilder()
                                                                            .setNode("testing-node")
                                                                            .setStatus(Status.Connected)
                                                                            .build()))
                                                    .build())
                                    .build())
                    .build());
        }

        @Nonnull
        @Override
        public ListenableFuture<Node> onNodeUpdated(@Nonnull NodeId nodeId, @Nonnull Node configNode) {
            LOG.debug("Updating node {} with config {}", nodeId, configNode);
            final NetconfNode augmentation = configNode.getAugmentation(NetconfNode.class);
            return Futures.immediateFuture(new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setConnectionStatus(ConnectionStatus.Connected)
                                    .setHost(augmentation.getHost())
                                    .setPort(augmentation.getPort())
                                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                    .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                    .setClusteredConnectionStatus(
                                            new ClusteredConnectionStatusBuilder()
                                                    .setNodeStatus(
                                                            Collections.singletonList(
                                                                    new NodeStatusBuilder()
                                                                            .setNode("testing-node")
                                                                            .setStatus(Status.Connected)
                                                                            .build()))
                                                    .build())
                                    .build())
                    .build());
        }

        @Nonnull
        @Override
        public ListenableFuture<Void> onNodeDeleted(@Nonnull NodeId nodeId) {
            LOG.debug("Deleting node {}", nodeId);
            return Futures.immediateFuture(null);
        }

        @Nonnull
        @Override
        public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull NodeId nodeId) {
            return null;
        }

        @Override
        public void onRoleChanged(RoleChangeDTO roleChangeDTO) {

        }

        @Override
        public void onReceive(Object o, ActorRef actorRef) {

        }

        @Override
        public void onDeviceConnected(SchemaContext remoteSchemaContext, NetconfSessionPreferences netconfSessionPreferences, DOMRpcService deviceRpc) {

        }

        @Override
        public void onDeviceDisconnected() {

        }

        @Override
        public void onDeviceFailed(Throwable throwable) {

        }

        @Override
        public void onNotification(DOMNotification domNotification) {

        }

        @Override
        public void close() {

        }
    }

    public static class TestingTopologyManagerCallback implements TopologyManagerCallback {

        public TestingTopologyManagerCallback() {

        }

        @Override
        public ListenableFuture<Node> onNodeCreated(NodeId nodeId, Node node) {
            LOG.warn("Actor system that called this: {}", TypedActor.context().system().settings().toString());
            return Futures.immediateFuture(new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setConnectionStatus(ConnectionStatus.Connected)
                                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                    .setPort(new PortNumber(2555))
                                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                    .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                    .build())
                    .build());
        }

        @Override
        public ListenableFuture<Node> onNodeUpdated(NodeId nodeId, Node node) {
            LOG.warn("Actor system that called this: {}", TypedActor.context().system().settings().toString());
            LOG.debug("Update called on node {}, with config {}", nodeId.getValue(), node);
            return Futures.immediateFuture(new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setConnectionStatus(ConnectionStatus.Connected)
                                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                    .setPort(new PortNumber(65535))
                                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                    .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                    .build())
                    .build());
        }

        @Override
        public ListenableFuture<Void> onNodeDeleted(NodeId nodeId) {
            LOG.debug("Delete called on node {}", nodeId.getValue());
            return Futures.immediateFuture(null);
        }

        @Nonnull
        @Override
        public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull NodeId nodeId) {
            return null;
        }

        @Override
        public void onRoleChanged(RoleChangeDTO roleChangeDTO) {

        }

        @Override
        public void onReceive(Object o, ActorRef actorRef) {

        }

        @Nonnull
        @Override
        public Node getInitialState(@Nonnull NodeId nodeId, @Nonnull Node configNode) {
            return new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setConnectionStatus(ConnectionStatus.Connecting)
                                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                    .setPort(new PortNumber(65535))
                                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                    .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                    .build())
                    .build();
        }

        @Nonnull
        @Override
        public Node getFailedState(@Nonnull NodeId nodeId, @Nonnull Node configNode) {
            return new NodeBuilder()
                    .setNodeId(nodeId)
                    .addAugmentation(NetconfNode.class,
                            new NetconfNodeBuilder()
                                    .setConnectionStatus(ConnectionStatus.UnableToConnect)
                                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                                    .setPort(new PortNumber(65535))
                                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                    .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                    .build())
                    .build();
        }
    }

    public class TestingSuccesfulStateAggregator implements StateAggregator {

        @Override
        public ListenableFuture<Node> combineCreateAttempts(List<ListenableFuture<Node>> stateFutures) {
            final SettableFuture<Node> future = SettableFuture.create();
            final ListenableFuture<List<Node>> allAsList = Futures.allAsList(stateFutures);
            Futures.addCallback(allAsList, new FutureCallback<List<Node>>() {
                @Override
                public void onSuccess(List<Node> result) {
                    for (int i = 0; i < result.size() - 1; i++) {
                        if (!result.get(i).equals(result.get(i + 1))) {
                            LOG.warn("Node 1 {}: {}", result.get(i).getClass(), result.get(i));
                            LOG.warn("Node 2 {}: {}", result.get(i + 1).getClass(), result.get(i + 1));
                            future.setException(new IllegalStateException("Create futures have different result"));
                            LOG.warn("Future1 : {}  Future2 : {}", result.get(i), result.get(i+1));
                        }
                    }
                    future.set(result.get(0));
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("One of the combined create attempts failed {}", t);
                    future.setException(t);
                }
            }, TypedActor.context().dispatcher());

            return future;
        }

        @Override
        public ListenableFuture<Node> combineUpdateAttempts(List<ListenableFuture<Node>> stateFutures) {
            final SettableFuture<Node> future = SettableFuture.create();
            final ListenableFuture<List<Node>> allAsList = Futures.allAsList(stateFutures);
            Futures.addCallback(allAsList, new FutureCallback<List<Node>>() {
                @Override
                public void onSuccess(List<Node> result) {
                    for (int i = 0; i < result.size() - 1; i++) {
                        if (!result.get(i).equals(result.get(i + 1))) {
                            future.setException(new IllegalStateException("Update futures have different result"));
                        }
                    }
                    future.set(result.get(0));
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("One of the combined update attempts failed {}", t);
                    future.setException(t);
                }
            });
            return future;
        }

        @Override
        public ListenableFuture<Void> combineDeleteAttempts(List<ListenableFuture<Void>> stateFutures) {
            final SettableFuture<Void> future = SettableFuture.create();
            final ListenableFuture<List<Void>> allAsList = Futures.allAsList(stateFutures);
            Futures.addCallback(allAsList, new FutureCallback<List<Void>>() {
                @Override
                public void onSuccess(List<Void> result) {
                    future.set(null);
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("One of the combined delete attempts failed {}", t);
                    future.setException(t);
                }
            });
            return future;
        }
    }
}
