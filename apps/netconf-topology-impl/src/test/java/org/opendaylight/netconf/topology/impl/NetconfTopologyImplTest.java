/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.util.Collection;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemas;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.IdentifiableItem;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfTopologyImplTest {
    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final String TOPOLOGY_ID = "testing-topology";

    @Mock
    private NetconfClientDispatcher mockedClientDispatcher;
    @Mock
    private EventExecutor mockedEventExecutor;
    @Mock
    private ScheduledThreadPool mockedKeepaliveExecutor;
    @Mock
    private ThreadPool mockedProcessingExecutor;
    @Mock
    private SchemaResourceManager mockedResourceManager;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private RpcProviderService rpcProviderService;
    @Mock
    private NetconfClientConfigurationBuilderFactory builderFactory;
    @Mock
    private WriteTransaction wtx;

    private TestingNetconfTopologyImpl topology;
    private TestingNetconfTopologyImpl spyTopology;

    @Before
    public void setUp() {
        doReturn(MoreExecutors.newDirectExecutorService()).when(mockedProcessingExecutor).getExecutor();
        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wtx).commit();

        topology = new TestingNetconfTopologyImpl(TOPOLOGY_ID, mockedClientDispatcher, mockedEventExecutor,
            mockedKeepaliveExecutor, mockedProcessingExecutor, mockedResourceManager, dataBroker, mountPointService,
            encryptionService, builderFactory, rpcProviderService);
        //verify initialization of topology
        verify(wtx).merge(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_ID))).build(),
                new TopologyBuilder().setTopologyId(new TopologyId(TOPOLOGY_ID)).build());

        spyTopology = spy(topology);
    }

    @Test
    public void testOnDataTreeChange() {
        final DataObjectModification<Node> newNode = mock(DataObjectModification.class);
        doReturn(DataObjectModification.ModificationType.WRITE).when(newNode).getModificationType();

        NodeKey key = new NodeKey(NODE_ID);
        PathArgument pa = IdentifiableItem.of(Node.class, key);
        doReturn(pa).when(newNode).getIdentifier();

        final NodeBuilder nn = new NodeBuilder()
                .withKey(key)
                .addAugmentation(new NetconfNodeBuilder()
                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                    .setPort(new PortNumber(Uint16.valueOf(9999)))
                    .setReconnectOnChangedSchema(true)
                    .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                    .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
                    .setKeepaliveDelay(Uint32.valueOf(1000))
                    .setTcpOnly(true)
                    .setCredentials(new LoginPasswordBuilder()
                        .setUsername("testuser")
                        .setPassword("testpassword")
                        .build())
                    .build());
        doReturn(nn.build()).when(newNode).getDataAfter();

        final Collection<DataTreeModification<Node>> changes = new HashSet<>();
        final DataTreeModification<Node> ch = mock(DataTreeModification.class);
        doReturn(newNode).when(ch).getRootNode();
        changes.add(ch);
        spyTopology.onDataTreeChanged(changes);
        verify(spyTopology).ensureNode(nn.build());

        doReturn(DataObjectModification.ModificationType.DELETE).when(newNode).getModificationType();
        spyTopology.onDataTreeChanged(changes);
        verify(spyTopology).deleteNode(NetconfTopologyImpl.getNodeId(pa));

        doReturn(DataObjectModification.ModificationType.SUBTREE_MODIFIED).when(newNode).getModificationType();
        spyTopology.onDataTreeChanged(changes);

        // one in previous creating and deleting node and one in updating
        verify(spyTopology, times(2)).ensureNode(nn.build());
    }

    public static class TestingNetconfTopologyImpl extends NetconfTopologyImpl {
        private static final BaseNetconfSchemas BASE_SCHEMAS;

        static {
            try {
                BASE_SCHEMAS = new DefaultBaseNetconfSchemas(new DefaultYangParserFactory());
            } catch (YangParserException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public TestingNetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                final ThreadPool processingExecutor, final SchemaResourceManager schemaRepositoryProvider,
                final DataBroker dataBroker, final DOMMountPointService mountPointService,
                final AAAEncryptionService encryptionService,
                final NetconfClientConfigurationBuilderFactory builderFactory,
                final RpcProviderService rpcProviderService) {
            super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountPointService, encryptionService, builderFactory,
                rpcProviderService, BASE_SCHEMAS);
        }

        @Override
        public void ensureNode(final Node configNode) {
            // No-op
        }

        @Override
        public void deleteNode(final NodeId nodeId) {
            // No-op
        }
    }
}
