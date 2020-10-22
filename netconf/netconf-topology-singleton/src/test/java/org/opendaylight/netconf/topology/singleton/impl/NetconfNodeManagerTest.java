/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.DELETE;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.SUBTREE_MODIFIED;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.WRITE;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.dispatch.Dispatchers;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import com.typesafe.config.ConfigFactory;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToIRTransformer;

/**
 * Unit tests for NetconfNodeManager.
 *
 * @author Thomas Pantelis
 */
public class NetconfNodeManagerTest extends AbstractBaseSchemasTest {
    private static final String ACTOR_SYSTEM_NAME = "test";
    private static final RemoteDeviceId DEVICE_ID = new RemoteDeviceId("device", new InetSocketAddress(65535));
    private static final List<SourceIdentifier> SOURCE_IDENTIFIERS =
            ImmutableList.of(RevisionSourceIdentifier.create("testID"));

    @Mock
    private DOMMountPointService mockMountPointService;

    @Mock
    private DOMMountPointService.DOMMountPointBuilder mockMountPointBuilder;

    @Mock
    private ObjectRegistration<DOMMountPoint> mockMountPointReg;

    @Mock
    private DataBroker mockDataBroker;

    @Mock
    private NetconfDataTreeService netconfService;

    @Mock
    private DOMDataBroker mockDeviceDataBroker;

    @Mock
    private DOMRpcService mockRpcService;

    @Mock
    private DOMActionService mockActionService;

    @Mock
    private NetconfDeviceSchemasResolver mockSchemasResolver;

    @Mock
    private EffectiveModelContextFactory mockSchemaContextFactory;

    private ActorSystem slaveSystem;
    private ActorSystem masterSystem;
    private TestActorRef<TestMasterActor> testMasterActorRef;
    private NetconfNodeManager netconfNodeManager;
    private String masterAddress;

    @Before
    public void setup() {
        initMocks(this);

        final Timeout responseTimeout = Timeout.apply(1, TimeUnit.SECONDS);

        slaveSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, ConfigFactory.load().getConfig("Slave"));
        masterSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, ConfigFactory.load().getConfig("Master"));

        masterAddress = Cluster.get(masterSystem).selfAddress().toString();

        final SharedSchemaRepository masterSchemaRepository = new SharedSchemaRepository("master");
        masterSchemaRepository.registerSchemaSourceListener(
                TextToIRTransformer.create(masterSchemaRepository, masterSchemaRepository));

        final String yangTemplate =
                  "module ID {"
                + "  namespace \"ID\";"
                + "  prefix ID;"
                + "}";

        SOURCE_IDENTIFIERS.stream().map(
            sourceId -> masterSchemaRepository.registerSchemaSource(
                id -> Futures.immediateFuture(YangTextSchemaSource.delegateForByteSource(id,
                        ByteSource.wrap(yangTemplate.replaceAll("ID", id.getName()).getBytes(UTF_8)))),
                PotentialSchemaSource.create(sourceId, YangTextSchemaSource.class, 1)))
        .collect(Collectors.toList());

        final NetconfTopologySetup masterSetup = new NetconfTopologySetup.NetconfTopologySetupBuilder()
                .setActorSystem(masterSystem).setDataBroker(mockDataBroker).setSchemaResourceDTO(
                        new NetconfDevice.SchemaResourcesDTO(masterSchemaRepository, masterSchemaRepository,
                                mockSchemaContextFactory, mockSchemasResolver)).setBaseSchemas(BASE_SCHEMAS).build();

        testMasterActorRef = TestActorRef.create(masterSystem, Props.create(TestMasterActor.class, masterSetup,
                DEVICE_ID, responseTimeout, mockMountPointService).withDispatcher(Dispatchers.DefaultDispatcherId()),
                NetconfTopologyUtils.createMasterActorName(DEVICE_ID.getName(), masterAddress));

        final SharedSchemaRepository slaveSchemaRepository = new SharedSchemaRepository("slave");
        slaveSchemaRepository.registerSchemaSourceListener(
                TextToIRTransformer.create(slaveSchemaRepository, slaveSchemaRepository));

        final NetconfTopologySetup slaveSetup = new NetconfTopologySetup.NetconfTopologySetupBuilder()
                .setActorSystem(slaveSystem).setDataBroker(mockDataBroker).setSchemaResourceDTO(
                        new NetconfDevice.SchemaResourcesDTO(slaveSchemaRepository, slaveSchemaRepository,
                                mockSchemaContextFactory, mockSchemasResolver)).setBaseSchemas(BASE_SCHEMAS).build();

        netconfNodeManager = new NetconfNodeManager(slaveSetup, DEVICE_ID, responseTimeout,
                mockMountPointService);

        setupMountPointMocks();
    }

    @After
    public void teardown() {
        TestKit.shutdownActorSystem(slaveSystem, true);
        TestKit.shutdownActorSystem(masterSystem, true);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSlaveMountPointRegistration() throws InterruptedException, ExecutionException, TimeoutException {
        initializeMaster();

        final ListenerRegistration<?> mockListenerReg = mock(ListenerRegistration.class);
        doReturn(mockListenerReg).when(mockDataBroker).registerDataTreeChangeListener(any(), any());

        final NodeId nodeId = new NodeId("device");
        final NodeKey nodeKey = new NodeKey(nodeId);
        final String topologyId = "topology-netconf";
        final InstanceIdentifier<Node> nodeListPath = NetconfTopologyUtils.createTopologyNodeListPath(
                nodeKey, topologyId);

        netconfNodeManager.registerDataTreeChangeListener(topologyId, nodeKey);
        verify(mockDataBroker).registerDataTreeChangeListener(any(), eq(netconfNodeManager));

        // Invoke onDataTreeChanged with a NetconfNode WRITE to simulate the master writing the operational state to
        // Connected. Expect the slave mount point created and registered.

        final NetconfNode netconfNode = newNetconfNode();
        final Node node = new NodeBuilder().setNodeId(nodeId).addAugmentation(netconfNode).build();

        final DataObjectModification<Node> mockDataObjModification = mock(DataObjectModification.class);
        doReturn(Iterables.getLast(nodeListPath.getPathArguments())).when(mockDataObjModification).getIdentifier();
        doReturn(WRITE).when(mockDataObjModification).getModificationType();
        doReturn(node).when(mockDataObjModification).getDataAfter();

        netconfNodeManager.onDataTreeChanged(Collections.singletonList(
                new NetconfTopologyManagerTest.CustomTreeModification(DataTreeIdentifier.create(
                        LogicalDatastoreType.OPERATIONAL, nodeListPath), mockDataObjModification)));

        verify(mockMountPointBuilder, timeout(5000)).register();
        verify(mockMountPointBuilder).addService(eq(DOMDataBroker.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMRpcService.class), any());
        verify(mockMountPointBuilder).addService(eq(DOMNotificationService.class), any());
        verify(mockMountPointService).createMountPoint(DEVICE_ID.getTopologyPath());

        // Notify that the NetconfNode operational state was deleted. Expect the slave mount point closed.

        doReturn(DELETE).when(mockDataObjModification).getModificationType();
        doReturn(node).when(mockDataObjModification).getDataBefore();
        doReturn(null).when(mockDataObjModification).getDataAfter();

        netconfNodeManager.onDataTreeChanged(Collections.singletonList(
                new NetconfTopologyManagerTest.CustomTreeModification(DataTreeIdentifier.create(
                        LogicalDatastoreType.OPERATIONAL, nodeListPath), mockDataObjModification)));

        verify(mockMountPointReg, timeout(5000)).close();

        // Notify with a NetconfNode operational state WRITE. Expect the slave mount point re-created.

        setupMountPointMocks();

        doReturn(WRITE).when(mockDataObjModification).getModificationType();
        doReturn(null).when(mockDataObjModification).getDataBefore();
        doReturn(node).when(mockDataObjModification).getDataAfter();

        netconfNodeManager.onDataTreeChanged(Collections.singletonList(
                new NetconfTopologyManagerTest.CustomTreeModification(DataTreeIdentifier.create(
                        LogicalDatastoreType.OPERATIONAL, nodeListPath), mockDataObjModification)));

        verify(mockMountPointBuilder, timeout(5000)).register();

        // Notify again with a NetconfNode operational state WRITE. Expect the prior slave mount point closed and
        // and a new one registered.

        setupMountPointMocks();

        doReturn(node).when(mockDataObjModification).getDataBefore();

        netconfNodeManager.onDataTreeChanged(Collections.singletonList(
                new NetconfTopologyManagerTest.CustomTreeModification(DataTreeIdentifier.create(
                        LogicalDatastoreType.OPERATIONAL, nodeListPath), mockDataObjModification)));

        verify(mockMountPointReg, timeout(5000)).close();
        verify(mockMountPointBuilder, timeout(5000)).register();

        // Notify that the NetconfNode operational state was changed to UnableToConnect. Expect the slave mount point
        // closed.

        setupMountPointMocks();

        final Node updatedNode = new NodeBuilder().setNodeId(nodeId)
                .addAugmentation(new NetconfNodeBuilder(netconfNode)
                    .setConnectionStatus(NetconfNodeConnectionStatus.ConnectionStatus.UnableToConnect)
                    .build())
                .build();

        doReturn(SUBTREE_MODIFIED).when(mockDataObjModification).getModificationType();
        doReturn(node).when(mockDataObjModification).getDataBefore();
        doReturn(updatedNode).when(mockDataObjModification).getDataAfter();

        netconfNodeManager.onDataTreeChanged(Collections.singletonList(
                new NetconfTopologyManagerTest.CustomTreeModification(DataTreeIdentifier.create(
                        LogicalDatastoreType.OPERATIONAL, nodeListPath), mockDataObjModification)));

        verify(mockMountPointReg, timeout(5000)).close();

        netconfNodeManager.close();
        verifyNoMoreInteractions(mockMountPointReg);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSlaveMountPointRegistrationFailuresAndRetries()
            throws InterruptedException, ExecutionException, TimeoutException {
        final NodeId nodeId = new NodeId("device");
        final NodeKey nodeKey = new NodeKey(nodeId);
        final String topologyId = "topology-netconf";
        final InstanceIdentifier<Node> nodeListPath = NetconfTopologyUtils.createTopologyNodeListPath(
                nodeKey, topologyId);

        final NetconfNode netconfNode = newNetconfNode();
        final Node node = new NodeBuilder().setNodeId(nodeId).addAugmentation(netconfNode).build();

        final DataObjectModification<Node> mockDataObjModification = mock(DataObjectModification.class);
        doReturn(Iterables.getLast(nodeListPath.getPathArguments())).when(mockDataObjModification).getIdentifier();
        doReturn(WRITE).when(mockDataObjModification).getModificationType();
        doReturn(node).when(mockDataObjModification).getDataAfter();

        // First try the registration where the perceived master hasn't been initialized as the master.

        netconfNodeManager.onDataTreeChanged(Collections.singletonList(
                new NetconfTopologyManagerTest.CustomTreeModification(DataTreeIdentifier.create(
                        LogicalDatastoreType.OPERATIONAL, nodeListPath), mockDataObjModification)));

        verify(mockMountPointBuilder, after(1000).never()).register();

        // Initialize the master but drop the initial YangTextSchemaSourceRequest message sent to the master so
        // it retries.

        initializeMaster();

        final CompletableFuture<AskForMasterMountPoint> yangTextSchemaSourceRequestFuture = new CompletableFuture<>();
        testMasterActorRef.underlyingActor().messagesToDrop.put(YangTextSchemaSourceRequest.class,
                yangTextSchemaSourceRequestFuture);

        netconfNodeManager.onDataTreeChanged(Collections.singletonList(
                new NetconfTopologyManagerTest.CustomTreeModification(DataTreeIdentifier.create(
                        LogicalDatastoreType.OPERATIONAL, nodeListPath), mockDataObjModification)));

        yangTextSchemaSourceRequestFuture.get(5, TimeUnit.SECONDS);
        verify(mockMountPointBuilder, timeout(5000)).register();

        // Initiate another registration but drop the initial AskForMasterMountPoint message sent to the master so
        // it retries.

        setupMountPointMocks();

        final CompletableFuture<AskForMasterMountPoint> askForMasterMountPointFuture = new CompletableFuture<>();
        testMasterActorRef.underlyingActor().messagesToDrop.put(AskForMasterMountPoint.class,
                askForMasterMountPointFuture);

        netconfNodeManager.onDataTreeChanged(Collections.singletonList(
                new NetconfTopologyManagerTest.CustomTreeModification(DataTreeIdentifier.create(
                        LogicalDatastoreType.OPERATIONAL, nodeListPath), mockDataObjModification)));

        askForMasterMountPointFuture.get(5, TimeUnit.SECONDS);
        verify(mockMountPointReg, timeout(5000)).close();
        verify(mockMountPointBuilder, timeout(5000)).register();

        setupMountPointMocks();
        netconfNodeManager.close();
        verify(mockMountPointReg, timeout(5000)).close();
    }

    private NetconfNode newNetconfNode() {
        return new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setConnectionStatus(NetconfNodeConnectionStatus.ConnectionStatus.Connected)
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder()
                        .setNetconfMasterNode(masterAddress).build())
                .build();
    }

    private void setupMountPointMocks() {
        reset(mockMountPointService, mockMountPointBuilder, mockMountPointReg);

        doNothing().when(mockMountPointReg).close();

        doReturn(mockMountPointBuilder).when(mockMountPointBuilder).addService(any(), any());
        doReturn(mockMountPointReg).when(mockMountPointBuilder).register();

        doReturn(mockMountPointBuilder).when(mockMountPointService).createMountPoint(any());
    }

    private void initializeMaster() {
        final TestKit kit = new TestKit(masterSystem);
        testMasterActorRef.tell(new CreateInitialMasterActorData(mockDeviceDataBroker, netconfService,
            SOURCE_IDENTIFIERS,
                mockRpcService, mockActionService), kit.getRef());

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
            final CompletableFuture dropFuture = messagesToDrop.remove(message.getClass());
            if (dropFuture != null) {
                dropFuture.complete(message);
            } else {
                super.handleReceive(message);
            }
        }
    }
}
