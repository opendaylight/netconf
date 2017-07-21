/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType.DELETE;
import static org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType.WRITE;

import com.google.common.util.concurrent.Futures;
import io.netty.util.concurrent.EventExecutor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.topology.singleton.config.rev170419.Config;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.topology.singleton.config.rev170419.ConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NetconfTopologyManagerTest {

    private final String topologyId = "topologyID";
    private NetconfTopologyManager netconfTopologyManager;

    @Mock
    private DataBroker dataBroker;

    @Mock
    private ClusterSingletonServiceProvider clusterSingletonServiceProvider;

    @Before
    public void setUp() {
        initMocks(this);

        final RpcProviderRegistry rpcProviderRegistry = mock(RpcProviderRegistry.class);
        final ScheduledThreadPool keepaliveExecutor = mock(ScheduledThreadPool.class);
        final ThreadPool processingExecutor = mock(ThreadPool.class);
        final ActorSystemProvider actorSystemProvider = mock(ActorSystemProvider.class);
        final EventExecutor eventExecutor = mock(EventExecutor.class);
        final NetconfClientDispatcher clientDispatcher = mock(NetconfClientDispatcher.class);
        final DOMMountPointService mountPointService = mock(DOMMountPointService.class);
        final AAAEncryptionService encryptionService = mock(AAAEncryptionService.class);

        final Config config = new ConfigBuilder().setWriteTransactionIdleTimeout(0).build();
        netconfTopologyManager = new NetconfTopologyManager(dataBroker, rpcProviderRegistry,
                clusterSingletonServiceProvider, keepaliveExecutor, processingExecutor,
                actorSystemProvider, eventExecutor, clientDispatcher, topologyId, config,
                mountPointService, encryptionService);
    }

    @Test
    public void testWriteConfiguration() throws Exception {
        writeConfiguration(false);
    }

    @Test
    public void testWriteConfigurationFail() throws Exception {
        writeConfiguration(true);
    }

    @Test
    public void testRegisterDataTreeChangeListener() {

        final WriteTransaction wtx = mock(WriteTransaction.class);

        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(wtx).merge(any(), any(), any());
        doReturn(Futures.immediateCheckedFuture(null)).when(wtx).submit();
        doReturn(null).when(dataBroker).registerDataChangeListener(any(), any(), any(), any());

        netconfTopologyManager.init();

        // verify if listener is called with right parameters = registered on right path

        verify(dataBroker, times(1)).registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, NetconfTopologyUtils
                        .createTopologyListPath(topologyId).child(Node.class)), netconfTopologyManager);

    }

    @Test
    public void testClose() throws Exception {

        final Field fieldContexts = NetconfTopologyManager.class.getDeclaredField("contexts");
        fieldContexts.setAccessible(true);
        @SuppressWarnings("unchecked") final Map<InstanceIdentifier<Node>, NetconfTopologyContext> contexts =
                (Map<InstanceIdentifier<Node>, NetconfTopologyContext>) fieldContexts.get(netconfTopologyManager);

        final Field fieldClusterRegistrations =
                NetconfTopologyManager.class.getDeclaredField("clusterRegistrations");
        fieldClusterRegistrations.setAccessible(true);
        @SuppressWarnings("unchecked")
        final Map<InstanceIdentifier<Node>, ClusterSingletonServiceRegistration> clusterRegistrations =
                (Map<InstanceIdentifier<Node>, ClusterSingletonServiceRegistration>)
                        fieldClusterRegistrations.get(netconfTopologyManager);

        final InstanceIdentifier<Node> instanceIdentifier = NetconfTopologyUtils.createTopologyNodeListPath(
                new NodeKey(new NodeId("node-id-1")), "topology-1");


        final NetconfTopologyContext context = mock(NetconfTopologyContext.class);
        final ClusterSingletonServiceRegistration clusterRegistration =
                mock(ClusterSingletonServiceRegistration.class);
        contexts.put(instanceIdentifier, context);
        clusterRegistrations.put(instanceIdentifier, clusterRegistration);

        doNothing().when(context).closeFinal();
        doNothing().when(clusterRegistration).close();

        netconfTopologyManager.close();
        verify(context, times(1)).closeFinal();
        verify(clusterRegistration, times(1)).close();

        assertTrue(contexts.isEmpty());
        assertTrue(clusterRegistrations.isEmpty());

    }

    private void writeConfiguration(final boolean fail) throws Exception {

        final ClusterSingletonServiceRegistration clusterRegistration = mock(ClusterSingletonServiceRegistration.class);

        final Field fieldContexts = NetconfTopologyManager.class.getDeclaredField("contexts");
        fieldContexts.setAccessible(true);
        @SuppressWarnings("unchecked") final Map<InstanceIdentifier<Node>, NetconfTopologyContext> contexts =
                (Map<InstanceIdentifier<Node>, NetconfTopologyContext>) fieldContexts.get(netconfTopologyManager);

        final Field fieldClusterRegistrations =
                NetconfTopologyManager.class.getDeclaredField("clusterRegistrations");
        fieldClusterRegistrations.setAccessible(true);
        @SuppressWarnings("unchecked")
        final Map<InstanceIdentifier<Node>, ClusterSingletonServiceRegistration> clusterRegistrations =
                (Map<InstanceIdentifier<Node>, ClusterSingletonServiceRegistration>)
                        fieldClusterRegistrations.get(netconfTopologyManager);

        final Collection<DataTreeModification<Node>> changes = new ArrayList<>();

        final InstanceIdentifier<Node> instanceIdentifier = NetconfTopologyUtils.createTopologyNodeListPath(
                new NodeKey(new NodeId("node-id-1")), "topology-1");

        final InstanceIdentifier<Node> instanceIdentifierDiferent = NetconfTopologyUtils.createTopologyNodeListPath(
                new NodeKey(new NodeId("node-id-2")), "topology-2");

        final DataTreeIdentifier<Node> rootIdentifier =
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, instanceIdentifier);

        final DataTreeIdentifier<Node> rootIdentifierDifferent =
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, instanceIdentifierDiferent);

        @SuppressWarnings("unchecked") final DataObjectModification<Node> objectModification =
                mock(DataObjectModification.class);

        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setSchemaless(false)
                .setTcpOnly(false)
                .setActorResponseWaitTime(10)
                .build();
        final Node node = new NodeBuilder().setNodeId(new NodeId("node-id"))
                .addAugmentation(NetconfNode.class, netconfNode).build();

        final Identifier key = new NodeKey(new NodeId("node-id"));

        @SuppressWarnings("unchecked") final InstanceIdentifier.IdentifiableItem<Node, NodeKey> pathArgument =
                new InstanceIdentifier.IdentifiableItem(Node.class, key);


        // testing WRITE on two identical rootIdentifiers and one different
        if (fail) {
            changes.add(new CustomTreeModification(rootIdentifier, objectModification));
        } else {
            changes.add(new CustomTreeModification(rootIdentifier, objectModification));
            changes.add(new CustomTreeModification(rootIdentifier, objectModification));
            changes.add(new CustomTreeModification(rootIdentifierDifferent, objectModification));
        }
        doReturn(WRITE).when(objectModification).getModificationType();
        doReturn(node).when(objectModification).getDataAfter();
        doReturn(pathArgument).when(objectModification).getIdentifier();

        if (fail) {
            doThrow(new RuntimeException("error")).when(clusterSingletonServiceProvider)
                    .registerClusterSingletonService(any());
        } else {
            doReturn(clusterRegistration).when(clusterSingletonServiceProvider).registerClusterSingletonService(any());
        }
        netconfTopologyManager.onDataTreeChanged(changes);

        if (fail) {
            verify(clusterSingletonServiceProvider, times(3))
                    .registerClusterSingletonService(any());
            assertTrue(contexts.isEmpty());
            assertTrue(clusterRegistrations.isEmpty());
        } else {
            verify(clusterSingletonServiceProvider, times(2))
                    .registerClusterSingletonService(any());

            // only two created contexts
            assertEquals(2, contexts.size());
            assertTrue(contexts.containsKey(rootIdentifier.getRootIdentifier()));
            assertTrue(contexts.containsKey(rootIdentifierDifferent.getRootIdentifier()));

            // only two created cluster registrations
            assertEquals(2, clusterRegistrations.size());
            assertTrue(clusterRegistrations.containsKey(rootIdentifier.getRootIdentifier()));
            assertTrue(clusterRegistrations.containsKey(rootIdentifierDifferent.getRootIdentifier()));

            // after delete there should be no context and clustered registrations
            doReturn(DELETE).when(objectModification).getModificationType();

            doNothing().when(clusterRegistration).close();

            netconfTopologyManager.onDataTreeChanged(changes);

            verify(clusterRegistration, times(2)).close();

            // empty map of contexts
            assertTrue(contexts.isEmpty());
            assertFalse(contexts.containsKey(rootIdentifier.getRootIdentifier()));
            assertFalse(contexts.containsKey(rootIdentifierDifferent.getRootIdentifier()));

            // empty map of clustered registrations
            assertTrue(clusterRegistrations.isEmpty());
            assertFalse(clusterRegistrations.containsKey(rootIdentifier.getRootIdentifier()));
            assertFalse(clusterRegistrations.containsKey(rootIdentifierDifferent.getRootIdentifier()));
        }
    }

    private class CustomTreeModification  implements DataTreeModification<Node> {

        private final DataTreeIdentifier<Node> rootPath;
        private final DataObjectModification<Node> rootNode;

        CustomTreeModification(final DataTreeIdentifier<Node> rootPath, final DataObjectModification<Node> rootNode) {
            this.rootPath = rootPath;
            this.rootNode = rootNode;
        }

        @Nonnull
        @Override
        public DataTreeIdentifier<Node> getRootPath() {
            return rootPath;
        }

        @Nonnull
        @Override
        public DataObjectModification<Node> getRootNode() {
            return rootNode;
        }
    }
}
