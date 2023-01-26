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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType.WRITE;

import akka.util.Timeout;
import com.google.common.collect.ImmutableSet;
import io.netty.util.concurrent.EventExecutor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.binding.spec.reflect.BindingReflections;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionProviderService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.impl.DefaultSchemaResourceManager;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.topology.singleton.config.rev170419.Config;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.topology.singleton.config.rev170419.ConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
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
                return ImmutableSet.of(BindingReflections.getModuleInfo(NetworkTopology.class),
                        BindingReflections.getModuleInfo(Topology.class));
            }
        };

        dataBrokerTest.setup();
        dataBroker = spy(dataBrokerTest.getDataBroker());

        final DOMRpcProviderService rpcProviderRegistry = mock(DOMRpcProviderService.class);
        final ScheduledThreadPool keepaliveExecutor = mock(ScheduledThreadPool.class);
        final DOMActionProviderService actionProviderRegistry = mock(DOMActionProviderService.class);
        final ThreadPool processingThreadPool = mock(ThreadPool.class);
        final ExecutorService processingService = mock(ExecutorService.class);
        doReturn(processingService).when(processingThreadPool).getExecutor();
        final ActorSystemProvider actorSystemProvider = mock(ActorSystemProvider.class);
        final EventExecutor eventExecutor = mock(EventExecutor.class);
        final NetconfClientDispatcher clientDispatcher = mock(NetconfClientDispatcher.class);
        final DOMMountPointService mountPointService = mock(DOMMountPointService.class);
        final AAAEncryptionService encryptionService = mock(AAAEncryptionService.class);
        final DeviceActionFactory deviceActionFactory = mock(DeviceActionFactory.class);
        final RpcProviderService rpcProviderService = mock(RpcProviderService.class);

        final Config config = new ConfigBuilder().setWriteTransactionIdleTimeout(Uint16.ZERO).build();
        netconfTopologyManager = new NetconfTopologyManager(BASE_SCHEMAS, dataBroker, rpcProviderRegistry,
                actionProviderRegistry, clusterSingletonServiceProvider, keepaliveExecutor, processingThreadPool,
                actorSystemProvider, eventExecutor, clientDispatcher, TOPOLOGY_ID, config,
                mountPointService, encryptionService, rpcProviderService, deviceActionFactory,
                new DefaultSchemaResourceManager(new DefaultYangParserFactory())) {
            @Override
            protected NetconfTopologyContext newNetconfTopologyContext(final NetconfTopologySetup setup,
                final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime,
                final DeviceActionFactory deviceActionFactory) {
                assertEquals(ACTOR_RESPONSE_WAIT_TIME.toJava(), actorResponseWaitTime.duration().toSeconds());
                return Objects.requireNonNull(mockContextMap.get(setup.getInstanceIdentifier()),
                        "No mock context for " + setup.getInstanceIdentifier()).apply(setup);
            }
        };

        doNothing().when(mockListenerReg).close();
        doReturn(mockListenerReg).when(dataBroker).registerDataTreeChangeListener(any(), any());
    }

    @Test
    public void testRegisterDataTreeChangeListener() throws Exception {

        netconfTopologyManager.init();

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

        netconfTopologyManager.init();

        netconfTopologyManager.onDataTreeChanged(Arrays.asList(
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
