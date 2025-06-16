/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.DELETE;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.SUBTREE_MODIFIED;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.WRITE;

import com.google.common.io.CharSource;
import com.google.common.util.concurrent.Futures;
import com.typesafe.config.ConfigFactory;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.dispatch.Dispatchers;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.spi.source.DelegatedYangTextSource;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToIRTransformer;

/**
 * Unit tests for NetconfNodeManager.
 *
 * @author Thomas Pantelis
 */
@ExtendWith(MockitoExtension.class)
class NetconfNodeManagerTest extends AbstractBaseSchemasTest {
    private static final String ACTOR_SYSTEM_NAME = "test";
    private static final RemoteDeviceId DEVICE_ID = new RemoteDeviceId("device", new InetSocketAddress(65535));
    private static final List<SourceIdentifier> SOURCE_IDENTIFIERS = List.of(new SourceIdentifier("testID"));

    @Mock
    private DOMMountPointService mockMountPointService;
    @Mock
    private DOMMountPointService.DOMMountPointBuilder mockMountPointBuilder;
    @Mock
    private ObjectRegistration<DOMMountPoint> mockMountPointReg;
    @Mock
    private DataBroker mockDataBroker;
    @Mock
    private DataStoreService dataStoreService;
    @Mock
    private DOMDataBroker mockDeviceDataBroker;
    @Mock
    private Rpcs.Normalized mockRpcService;
    @Mock
    private Actions.Normalized mockActionService;
    @Mock
    private NetconfDeviceSchemasResolver mockSchemasResolver;
    @Mock
    private EffectiveModelContextFactory mockSchemaContextFactory;

    private ActorSystem slaveSystem;
    private ActorSystem masterSystem;
    private TestActorRef<TestMasterActor> testMasterActorRef;
    private NetconfNodeManager netconfNodeManager;
    private String masterAddress;

    @BeforeEach
    void setup() {
        final Timeout responseTimeout = Timeout.apply(1, TimeUnit.SECONDS);

        slaveSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, ConfigFactory.load().getConfig("Slave"));
        masterSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, ConfigFactory.load().getConfig("Master"));

        masterAddress = Cluster.get(masterSystem).selfAddress().toString();

        SharedSchemaRepository masterSchemaRepository = new SharedSchemaRepository("master");
        masterSchemaRepository.registerSchemaSourceListener(
                TextToIRTransformer.create(masterSchemaRepository, masterSchemaRepository));

        final String yangTemplate = """
            module ID {\
              namespace "ID";\
              prefix ID;\
            }""";

        SOURCE_IDENTIFIERS.stream().map(
            sourceId -> masterSchemaRepository.registerSchemaSource(
                id -> Futures.immediateFuture(new DelegatedYangTextSource(id,
                        CharSource.wrap(yangTemplate.replaceAll("ID", id.name().getLocalName())))),
                PotentialSchemaSource.create(sourceId, YangTextSource.class, 1)))
        .collect(Collectors.toList());

        NetconfTopologySetup masterSetup = NetconfTopologySetup.builder()
                .setActorSystem(masterSystem)
                .setDataBroker(mockDataBroker)
                .setBaseSchemaProvider(BASE_SCHEMAS)
                .setDeviceSchemaProvider(createDeviceSchemaProvider(masterSchemaRepository))
                .build();

        testMasterActorRef = TestActorRef.create(masterSystem, Props.create(TestMasterActor.class, masterSetup,
                DEVICE_ID, responseTimeout, mockMountPointService).withDispatcher(Dispatchers.DefaultDispatcherId()),
                NetconfTopologyUtils.createMasterActorName(DEVICE_ID.name(), masterAddress));

        SharedSchemaRepository slaveSchemaRepository = new SharedSchemaRepository("slave");
        slaveSchemaRepository.registerSchemaSourceListener(
                TextToIRTransformer.create(slaveSchemaRepository, slaveSchemaRepository));

        final var provider = createDeviceSchemaProvider(slaveSchemaRepository);
        doReturn(slaveSchemaRepository).when(provider).registry();

        NetconfTopologySetup slaveSetup = NetconfTopologySetup.builder()
                .setActorSystem(slaveSystem)
                .setDataBroker(mockDataBroker)
                .setBaseSchemaProvider(BASE_SCHEMAS)
                .setDeviceSchemaProvider(provider)
                .build();

        netconfNodeManager = new NetconfNodeManager(slaveSetup, DEVICE_ID, responseTimeout,
                mockMountPointService);

        setupMountPointMocks();
    }

    private static DeviceNetconfSchemaProvider createDeviceSchemaProvider(final SharedSchemaRepository repository) {
        final var provider = mock(DeviceNetconfSchemaProvider.class);
        doReturn(repository).when(provider).repository();
        return provider;
    }

    @AfterEach
    void teardown() {
        TestKit.shutdownActorSystem(slaveSystem, true);
        TestKit.shutdownActorSystem(masterSystem, true);
    }

    @Test
    void testSlaveMountPointRegistration() {
        initializeMaster();

        Registration mockListenerReg = mock(Registration.class);
        doReturn(mockListenerReg).when(mockDataBroker).registerTreeChangeListener(any(), any(), any());

        final NodeId nodeId = new NodeId("device");
        final NodeKey nodeKey = new NodeKey(nodeId);
        final String topologyId = "topology-netconf";
        final var nodeListPath = NetconfTopologyUtils.createTopologyNodeListPath(nodeKey, topologyId);

        netconfNodeManager.registerDataTreeChangeListener(topologyId, nodeKey);
        verify(mockDataBroker).registerTreeChangeListener(any(), any(), eq(netconfNodeManager));

        // Invoke onDataTreeChanged with a NetconfNode WRITE to simulate the master writing the operational state to
        // Connected. Expect the slave mount point created and registered.

        final NetconfNodeAugment netconfNodeAugment = newNetconfNode();
        final Node node = new NodeBuilder().setNodeId(nodeId).addAugmentation(netconfNodeAugment).build();

        DataObjectWritten<Node> mockDataObjModification = mock();
        doReturn(nodeListPath.lastStep()).when(mockDataObjModification).step();
        doReturn(WRITE).when(mockDataObjModification).modificationType();
        doReturn(node).when(mockDataObjModification).dataAfter();

        netconfNodeManager.onDataTreeChanged(List.of(
                new NetconfTopologyManagerTest.CustomTreeModification(LogicalDatastoreType.OPERATIONAL,
                    nodeListPath.toIdentifier(), mockDataObjModification)));

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockMountPointBuilder).addService(eq(DOMDataBroker.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMNotificationService.class), any());
        verify(mockMountPointService).createMountPoint(NetconfNodeUtils.defaultTopologyMountPath(DEVICE_ID));

        // Notify that the NetconfNode operational state was deleted. Expect the slave mount point closed.

        doReturn(DELETE).when(mockDataObjModification).modificationType();

        netconfNodeManager.onDataTreeChanged(List.of(
            new NetconfTopologyManagerTest.CustomTreeModification(LogicalDatastoreType.OPERATIONAL,
                nodeListPath.toIdentifier(), mockDataObjModification)));

        verify(mockMountPointReg, timeout(5000)).close();

        // Notify with a NetconfNode operational state WRITE. Expect the slave mount point re-created.

        setupMountPointMocks();

        doReturn(WRITE).when(mockDataObjModification).modificationType();
        doReturn(null).when(mockDataObjModification).dataBefore();
        doReturn(node).when(mockDataObjModification).dataAfter();

        netconfNodeManager.onDataTreeChanged(List.of(
            new NetconfTopologyManagerTest.CustomTreeModification(LogicalDatastoreType.OPERATIONAL,
                nodeListPath.toIdentifier(), mockDataObjModification)));

        verify(mockMountPointBuilder, timeout(5000)).register();

        // Notify again with a NetconfNode operational state WRITE. Expect the prior slave mount point closed and
        // and a new one registered.

        setupMountPointMocks();

        doReturn(node).when(mockDataObjModification).dataBefore();

        netconfNodeManager.onDataTreeChanged(List.of(
            new NetconfTopologyManagerTest.CustomTreeModification(LogicalDatastoreType.OPERATIONAL,
                nodeListPath.toIdentifier(), mockDataObjModification)));

        verify(mockMountPointReg, timeout(5000)).close();
        verify(mockMountPointBuilder, timeout(5000)).register();

        // Notify that the NetconfNode operational state was changed to UnableToConnect. Expect the slave mount point
        // closed.

        reset(mockMountPointService, mockMountPointBuilder, mockMountPointReg);
        doNothing().when(mockMountPointReg).close();

        final Node updatedNode = new NodeBuilder().setNodeId(nodeId)
                .addAugmentation(new NetconfNodeAugmentBuilder()
                    .setNetconfNode(new NetconfNodeBuilder(netconfNodeAugment.getNetconfNode())
                        .setConnectionStatus(ConnectionStatus.UnableToConnect)
                        .build())
                    .build())
                .build();

        doReturn(SUBTREE_MODIFIED).when(mockDataObjModification).modificationType();
        doReturn(node).when(mockDataObjModification).dataBefore();
        doReturn(updatedNode).when(mockDataObjModification).dataAfter();

        netconfNodeManager.onDataTreeChanged(List.of(
            new NetconfTopologyManagerTest.CustomTreeModification(LogicalDatastoreType.OPERATIONAL,
                nodeListPath.toIdentifier(), mockDataObjModification)));

        verify(mockMountPointReg, timeout(5000)).close();

        netconfNodeManager.close();
        verifyNoMoreInteractions(mockMountPointReg);
    }

    @Test
    void testSlaveMountPointRegistrationFailuresAndRetries() throws Exception {
        final NodeId nodeId = new NodeId("device");
        final NodeKey nodeKey = new NodeKey(nodeId);
        final String topologyId = "topology-netconf";
        final var nodeListPath = NetconfTopologyUtils.createTopologyNodeListPath(nodeKey, topologyId);

        final NetconfNodeAugment netconfNodeAugment = newNetconfNode();
        final Node node = new NodeBuilder().setNodeId(nodeId).addAugmentation(netconfNodeAugment).build();

        DataObjectWritten<Node> mockDataObjModification = mock();
        doReturn(nodeListPath.lastStep()).when(mockDataObjModification).step();
        doReturn(WRITE).when(mockDataObjModification).modificationType();
        doReturn(node).when(mockDataObjModification).dataAfter();

        // First try the registration where the perceived master hasn't been initialized as the master.

        netconfNodeManager.onDataTreeChanged(List.of(
            new NetconfTopologyManagerTest.CustomTreeModification(LogicalDatastoreType.OPERATIONAL,
                nodeListPath.toIdentifier(), mockDataObjModification)));

        verify(mockMountPointBuilder, after(1000).never()).register();

        // Initialize the master but drop the initial YangTextSchemaSourceRequest message sent to the master so
        // it retries.

        initializeMaster();

        CompletableFuture<AskForMasterMountPoint> yangTextSchemaSourceRequestFuture = new CompletableFuture<>();
        testMasterActorRef.underlyingActor().messagesToDrop.put(YangTextSchemaSourceRequest.class,
                yangTextSchemaSourceRequestFuture);

        netconfNodeManager.onDataTreeChanged(List.of(
            new NetconfTopologyManagerTest.CustomTreeModification(LogicalDatastoreType.OPERATIONAL,
                nodeListPath.toIdentifier(), mockDataObjModification)));

        yangTextSchemaSourceRequestFuture.get(5, TimeUnit.SECONDS);
        verify(mockMountPointBuilder, timeout(5000)).register();

        // Initiate another registration but drop the initial AskForMasterMountPoint message sent to the master so
        // it retries.

        setupMountPointMocks();

        CompletableFuture<AskForMasterMountPoint> askForMasterMountPointFuture = new CompletableFuture<>();
        testMasterActorRef.underlyingActor().messagesToDrop.put(AskForMasterMountPoint.class,
                askForMasterMountPointFuture);

        netconfNodeManager.onDataTreeChanged(List.of(
            new NetconfTopologyManagerTest.CustomTreeModification(LogicalDatastoreType.OPERATIONAL,
                nodeListPath.toIdentifier(), mockDataObjModification)));

        askForMasterMountPointFuture.get(5, TimeUnit.SECONDS);
        verify(mockMountPointReg, timeout(5000)).close();
        verify(mockMountPointBuilder, timeout(5000)).register();

        reset(mockMountPointService, mockMountPointBuilder, mockMountPointReg);
        doNothing().when(mockMountPointReg).close();
        netconfNodeManager.close();
        verify(mockMountPointReg, timeout(5000)).close();
    }

    private NetconfNodeAugment newNetconfNode() {
        return new NetconfNodeAugmentBuilder()
            .setNetconfNode(new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setConnectionStatus(ConnectionStatus.Connected)
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder()
                        .setNetconfMasterNode(masterAddress).build())
                .build())
            .build();
    }

    private void setupMountPointMocks() {
        reset(mockMountPointService, mockMountPointBuilder, mockMountPointReg);
        doNothing().when(mockMountPointReg).close();
        doReturn(mockMountPointReg).when(mockMountPointBuilder).register();
        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
    }

    private void initializeMaster() {
        TestKit kit = new TestKit(masterSystem);
        testMasterActorRef.tell(new CreateInitialMasterActorData(mockDeviceDataBroker, dataStoreService,
            SOURCE_IDENTIFIERS, new RemoteDeviceServices(mockRpcService, mockActionService)), kit.getRef());

        kit.expectMsgClass(MasterActorDataInitialized.class);
    }

    private static class TestMasterActor extends NetconfNodeActor {
        final Map<Class<?>, CompletableFuture<? extends Object>> messagesToDrop = new ConcurrentHashMap<>();

        TestMasterActor(final NetconfTopologySetup setup, final RemoteDeviceId deviceId,
                final Timeout actorResponseWaitTime, final DOMMountPointService mountPointService) {
            super(setup, deviceId, actorResponseWaitTime, mountPointService);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public void handleReceive(final Object message) {
            CompletableFuture dropFuture = messagesToDrop.remove(message.getClass());
            if (dropFuture != null) {
                dropFuture.complete(message);
            } else {
                super.handleReceive(message);
            }
        }
    }
}
