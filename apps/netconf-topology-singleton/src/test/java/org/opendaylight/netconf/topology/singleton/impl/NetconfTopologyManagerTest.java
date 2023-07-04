/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.DELETE;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.SUBTREE_MODIFIED;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.WRITE;

import akka.actor.ActorSystem;
import akka.util.Timeout;
import io.netty.util.concurrent.EventExecutor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.impl.DefaultSchemaResourceManager;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfTopologyManagerTest extends AbstractBaseSchemasTest {
    private static final Uint16 ACTOR_RESPONSE_WAIT_TIME = Uint16.valueOf(10);
    private static final String TOPOLOGY_ID = "topologyID";

    private NetconfTopologyManager netconfTopologyManager;

    @Mock
    private ClusterSingletonServiceProvider clusterSingletonServiceProvider;

    @Mock
    private ListenerRegistration<?> mockListenerReg;

    private DataBroker dataBroker;

    private final Map<InstanceIdentifier<Node>, Function<NetconfTopologySetup, NetconfTopologyContext>>
            mockContextMap = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        AbstractDataBrokerTest dataBrokerTest = new AbstractDataBrokerTest() {
            @Override
            protected Set<YangModuleInfo> getModuleInfos() throws Exception {
                return Set.of(BindingRuntimeHelpers.getYangModuleInfo(NetworkTopology.class));
            }
        };

        dataBrokerTest.setup();
        dataBroker = spy(dataBrokerTest.getDataBroker());

        final ScheduledExecutorService keepaliveExecutor = mock(ScheduledExecutorService.class);
        final ExecutorService processingService = mock(ExecutorService.class);
        final ActorSystem actorSystem = mock(ActorSystem.class);
        final EventExecutor eventExecutor = mock(EventExecutor.class);
        final NetconfClientDispatcher clientDispatcher = mock(NetconfClientDispatcher.class);
        final DOMMountPointService mountPointService = mock(DOMMountPointService.class);
        final AAAEncryptionService encryptionService = mock(AAAEncryptionService.class);
        final DeviceActionFactory deviceActionFactory = mock(DeviceActionFactory.class);
        final RpcProviderService rpcProviderService = mock(RpcProviderService.class);
        final NetconfClientConfigurationBuilderFactory builderFactory =
            mock(NetconfClientConfigurationBuilderFactory.class);

        doNothing().when(mockListenerReg).close();
        doReturn(mockListenerReg).when(dataBroker).registerDataTreeChangeListener(any(), any());

        netconfTopologyManager = new NetconfTopologyManager(BASE_SCHEMAS, dataBroker, clusterSingletonServiceProvider,
                keepaliveExecutor, processingService, actorSystem, eventExecutor, clientDispatcher,
                mountPointService, encryptionService, rpcProviderService, deviceActionFactory,
                new DefaultSchemaResourceManager(new DefaultYangParserFactory()), builderFactory,
                TOPOLOGY_ID, Uint16.ZERO) {
            @Override
            protected NetconfTopologyContext newNetconfTopologyContext(final NetconfTopologySetup setup,
                final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime,
                final DeviceActionFactory deviceActionFactory) {
                assertEquals(ACTOR_RESPONSE_WAIT_TIME.toJava(), actorResponseWaitTime.duration().toSeconds());
                return Objects.requireNonNull(mockContextMap.get(setup.getInstanceIdentifier()),
                        "No mock context for " + setup.getInstanceIdentifier()).apply(setup);
            }
        };
    }

    @Test
    public void testRegisterDataTreeChangeListener() throws Exception {
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            try (ReadTransaction readTx = dataBroker.newReadOnlyTransaction()) {
                return readTx.exists(LogicalDatastoreType.OPERATIONAL,
                    NetconfTopologyUtils.createTopologyListPath(TOPOLOGY_ID)).get(3, TimeUnit.SECONDS);
            }
        });

        // verify registration is called with right parameters

        verify(dataBroker).registerDataTreeChangeListener(
                DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, NetconfTopologyUtils
                        .createTopologyListPath(TOPOLOGY_ID).child(Node.class)), netconfTopologyManager);

        netconfTopologyManager.close();
        verify(mockListenerReg).close();

        netconfTopologyManager.close();
        verifyNoMoreInteractions(mockListenerReg);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOnDataTreeChanged() {
        // Notify of 2 created Node objects.
        final NodeId nodeId1 = new NodeId("node-id-1");
        final InstanceIdentifier<Node> nodeInstanceId1 = NetconfTopologyUtils.createTopologyNodeListPath(
                new NodeKey(nodeId1), TOPOLOGY_ID);

        final NodeId nodeId2 = new NodeId("node-id-2");
        final InstanceIdentifier<Node> nodeInstanceId2 = NetconfTopologyUtils.createTopologyNodeListPath(
                new NodeKey(nodeId2), TOPOLOGY_ID);

        final NetconfNode netconfNode1 = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(1111)))
                .setActorResponseWaitTime(ACTOR_RESPONSE_WAIT_TIME)
                .build();
        final Node node1 = new NodeBuilder().setNodeId(nodeId1).addAugmentation(netconfNode1).build();

        final DataObjectModification<Node> dataObjectModification1 = mock(DataObjectModification.class);
        doReturn(WRITE).when(dataObjectModification1).getModificationType();
        doReturn(node1).when(dataObjectModification1).getDataAfter();
        doReturn(InstanceIdentifier.IdentifiableItem.of(Node.class, new NodeKey(nodeId1)))
                .when(dataObjectModification1).getIdentifier();

        final NetconfNode netconfNode2 = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(2222)))
                .setActorResponseWaitTime(ACTOR_RESPONSE_WAIT_TIME)
                .build();
        final Node node2 = new NodeBuilder().setNodeId(nodeId2).addAugmentation(netconfNode2).build();

        final DataObjectModification<Node> dataObjectModification2 = mock(DataObjectModification.class);
        doReturn(WRITE).when(dataObjectModification2).getModificationType();
        doReturn(node2).when(dataObjectModification2).getDataAfter();
        doReturn(InstanceIdentifier.IdentifiableItem.of(Node.class, new NodeKey(nodeId2)))
                .when(dataObjectModification2).getIdentifier();

        final NetconfTopologyContext mockContext1 = mock(NetconfTopologyContext.class);
        mockContextMap.put(nodeInstanceId1, setup -> {
            assertEquals(node1, setup.getNode());
            assertEquals(TOPOLOGY_ID, setup.getTopologyId());
            return mockContext1;
        });

        final NetconfTopologyContext mockContext2 = mock(NetconfTopologyContext.class);
        mockContextMap.put(nodeInstanceId2, setup -> {
            assertEquals(node2, setup.getNode());
            assertEquals(TOPOLOGY_ID, setup.getTopologyId());
            return mockContext2;
        });

        ClusterSingletonServiceRegistration mockClusterRegistration1 = mock(ClusterSingletonServiceRegistration.class);
        ClusterSingletonServiceRegistration mockClusterRegistration2 = mock(ClusterSingletonServiceRegistration.class);

        doReturn(mockClusterRegistration1).when(clusterSingletonServiceProvider)
                .registerClusterSingletonService(mockContext1);
        doReturn(mockClusterRegistration2).when(clusterSingletonServiceProvider)
                .registerClusterSingletonService(mockContext2);

        netconfTopologyManager.onDataTreeChanged(List.of(
                new CustomTreeModification(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                        nodeInstanceId1), dataObjectModification1),
                new CustomTreeModification(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                        nodeInstanceId2), dataObjectModification2)));

        verify(clusterSingletonServiceProvider).registerClusterSingletonService(mockContext1);
        verify(clusterSingletonServiceProvider).registerClusterSingletonService(mockContext2);

        // Notify of Node 1 replaced and Node 2 subtree modified.
        mockContextMap.clear();

        final NetconfNode updatedNetconfNode1 = new NetconfNodeBuilder(netconfNode1)
                .setPort(new PortNumber(Uint16.valueOf(33333))).build();
        final Node updatedNode1 = new NodeBuilder().setNodeId(nodeId1).addAugmentation(updatedNetconfNode1).build();

        doReturn(WRITE).when(dataObjectModification1).getModificationType();
        doReturn(updatedNode1).when(dataObjectModification1).getDataAfter();

        doReturn(SUBTREE_MODIFIED).when(dataObjectModification2).getModificationType();
        doReturn(node2).when(dataObjectModification2).getDataAfter();

        doNothing().when(mockContext1).refresh(any());
        doNothing().when(mockContext2).refresh(any());

        netconfTopologyManager.onDataTreeChanged(List.of(
                new CustomTreeModification(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                        nodeInstanceId1), dataObjectModification1),
                new CustomTreeModification(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                        nodeInstanceId2), dataObjectModification2)));

        ArgumentCaptor<NetconfTopologySetup> mockContext1Setup = ArgumentCaptor.forClass(NetconfTopologySetup.class);
        verify(mockContext1).refresh(mockContext1Setup.capture());
        assertEquals(updatedNode1, mockContext1Setup.getValue().getNode());

        verify(mockContext2).refresh(any());

        verifyNoMoreInteractions(clusterSingletonServiceProvider);

        // Notify of Node 1 deleted.
        doReturn(DELETE).when(dataObjectModification1).getModificationType();

        netconfTopologyManager.onDataTreeChanged(List.of(
                new CustomTreeModification(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                        nodeInstanceId1), dataObjectModification1)));

        verify(mockClusterRegistration1).close();
        verify(mockContext1).close();
        verifyNoMoreInteractions(clusterSingletonServiceProvider, mockClusterRegistration2, mockContext2);

        // Notify of Node 1 created again.
        reset(clusterSingletonServiceProvider);

        final NetconfTopologyContext newMockContext1 = mock(NetconfTopologyContext.class);
        final ClusterSingletonServiceRegistration newMockClusterRegistration1 =
                mock(ClusterSingletonServiceRegistration.class);

        doThrow(new RuntimeException("mock error")).doReturn(newMockClusterRegistration1)
                .when(clusterSingletonServiceProvider).registerClusterSingletonService(newMockContext1);

        doReturn(WRITE).when(dataObjectModification1).getModificationType();
        doReturn(node1).when(dataObjectModification1).getDataAfter();

        mockContextMap.put(nodeInstanceId1, setup -> {
            assertEquals(node1, setup.getNode());
            assertEquals(TOPOLOGY_ID, setup.getTopologyId());
            return newMockContext1;
        });

        netconfTopologyManager.onDataTreeChanged(List.of(
                new CustomTreeModification(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                        nodeInstanceId1), dataObjectModification1)));

        verify(clusterSingletonServiceProvider, times(2)).registerClusterSingletonService(newMockContext1);
        verifyNoMoreInteractions(mockClusterRegistration1, mockContext1, mockClusterRegistration2, mockContext2,
                newMockContext1, newMockClusterRegistration1, clusterSingletonServiceProvider);

        // Test close.
        netconfTopologyManager.close();

        verify(newMockClusterRegistration1).close();
        verify(newMockContext1).close();
        verify(mockClusterRegistration2).close();
        verify(mockContext2).close();

        netconfTopologyManager.close();

        verifyNoMoreInteractions(mockClusterRegistration1, mockContext1, mockClusterRegistration2, mockContext2,
                newMockContext1, newMockClusterRegistration1, clusterSingletonServiceProvider);
    }

    @Test
    public void testClusterSingletonServiceRegistrationFailure() throws Exception {
        final NodeId nodeId = new NodeId("node-id");
        final InstanceIdentifier<Node> nodeInstanceId = NetconfTopologyUtils.createTopologyNodeListPath(
                new NodeKey(nodeId), TOPOLOGY_ID);

        final Node node = new NodeBuilder()
                .setNodeId(nodeId)
                .addAugmentation(new NetconfNodeBuilder()
                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                    .setPort(new PortNumber(Uint16.valueOf(10)))
                    .setActorResponseWaitTime(ACTOR_RESPONSE_WAIT_TIME)
                    .build())
                .build();

        final DataObjectModification<Node> dataObjectModification = mock(DataObjectModification.class);
        doReturn(WRITE).when(dataObjectModification).getModificationType();
        doReturn(node).when(dataObjectModification).getDataAfter();
        doReturn(InstanceIdentifier.IdentifiableItem.of(Node.class, new NodeKey(nodeId)))
                .when(dataObjectModification).getIdentifier();

        final NetconfTopologyContext mockContext = mock(NetconfTopologyContext.class);
        mockContextMap.put(nodeInstanceId, setup -> mockContext);

        doThrow(new RuntimeException("mock error")).when(clusterSingletonServiceProvider)
                .registerClusterSingletonService(mockContext);

        netconfTopologyManager.onDataTreeChanged(List.of(
                new CustomTreeModification(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                        nodeInstanceId), dataObjectModification)));

        verify(clusterSingletonServiceProvider, times(3)).registerClusterSingletonService(mockContext);
        verify(mockContext).close();
        verifyNoMoreInteractions(mockListenerReg);

        netconfTopologyManager.close();
        verifyNoMoreInteractions(mockContext);
    }

    static class CustomTreeModification  implements DataTreeModification<Node> {

        private final DataTreeIdentifier<Node> rootPath;
        private final DataObjectModification<Node> rootNode;

        CustomTreeModification(final DataTreeIdentifier<Node> rootPath, final DataObjectModification<Node> rootNode) {
            this.rootPath = rootPath;
            this.rootNode = rootNode;
        }

        @Override
        public DataTreeIdentifier<Node> getRootPath() {
            return rootPath;
        }

        @Override
        public DataObjectModification<Node> getRootNode() {
            return rootNode;
        }
    }
}
