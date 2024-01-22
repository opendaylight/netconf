/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.DefaultBaseNetconfSchemas;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.credentials.credentials.LoginPasswordBuilder;
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
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Empty;
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
            encryptionService, rpcProviderService);
        //verify initialization of topology
        verify(wtx).merge(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_ID))).build(),
                new TopologyBuilder().setTopologyId(new TopologyId(TOPOLOGY_ID)).build());

        spyTopology = spy(topology);
    }

    @Test
    public void testOnDataTreeChange() {
        final DataObjectModification<Node> newNode = mock(DataObjectModification.class);
        when(newNode.getModificationType()).thenReturn(DataObjectModification.ModificationType.WRITE);

        NodeKey key = new NodeKey(NODE_ID);
        PathArgument pa = IdentifiableItem.of(Node.class, key);
        when(newNode.getIdentifier()).thenReturn(pa);

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

        when(newNode.getDataAfter()).thenReturn(nn.build());

        final Collection<DataTreeModification<Node>> changes = new HashSet<>();
        final DataTreeModification<Node> ch = mock(DataTreeModification.class);
        when(ch.getRootNode()).thenReturn(newNode);
        changes.add(ch);
        spyTopology.onDataTreeChanged(changes);
        verify(spyTopology).connectNode(NetconfTopologyImpl.getNodeId(pa), nn.build());

        when(newNode.getModificationType()).thenReturn(DataObjectModification.ModificationType.DELETE);
        spyTopology.onDataTreeChanged(changes);
        verify(spyTopology).disconnectNode(NetconfTopologyImpl.getNodeId(pa));

        when(newNode.getModificationType()).thenReturn(DataObjectModification.ModificationType.SUBTREE_MODIFIED);
        spyTopology.onDataTreeChanged(changes);

        //one in previous creating and deleting node and one in updating
        verify(spyTopology, times(2)).disconnectNode(NetconfTopologyImpl.getNodeId(pa));
        verify(spyTopology, times(2)).connectNode(NetconfTopologyImpl.getNodeId(pa), nn.build());
    }

    @Test
    public void testGetClientConfig() {
        final NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        final NetconfNodeBuilder nodeBuilder = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
                .setKeepaliveDelay(Uint32.valueOf(1000))
                .setCredentials(new LoginPasswordBuilder().setUsername("testuser").setPassword("testpassword").build())
                .setMaxConnectionAttempts(Uint32.ZERO)
                .setSleepFactor(Decimal64.valueOf("1.5"))
                .setConnectionTimeoutMillis(Uint32.valueOf(20000))
                .setMaxTimeoutBetweenAttemptsMillis(Uint32.valueOf(120000));

        final NetconfReconnectingClientConfiguration configuration =
                spyTopology.getClientConfig(sessionListener, nodeBuilder.setTcpOnly(true).build(), NODE_ID);
        assertEquals(NetconfClientConfiguration.NetconfClientProtocol.TCP, configuration.getProtocol());
        assertNotNull(configuration.getAuthHandler());
        assertNull(configuration.getSslHandlerFactory());

        final NetconfReconnectingClientConfiguration configuration2 =
                spyTopology.getClientConfig(sessionListener, nodeBuilder.setTcpOnly(false).build(), NODE_ID);
        assertEquals(NetconfClientConfiguration.NetconfClientProtocol.SSH, configuration2.getProtocol());
        assertNotNull(configuration2.getAuthHandler());
        assertNull(configuration2.getSslHandlerFactory());

        final NetconfReconnectingClientConfiguration configuration3 =
                spyTopology.getClientConfig(sessionListener, nodeBuilder
                        .setProtocol(new ProtocolBuilder().setName(Name.SSH).build()).build(), NODE_ID);
        assertEquals(NetconfClientConfiguration.NetconfClientProtocol.SSH, configuration3.getProtocol());
        assertNotNull(configuration3.getAuthHandler());
        assertNull(configuration3.getSslHandlerFactory());

        final NetconfReconnectingClientConfiguration configuration4 =
                spyTopology.getClientConfig(sessionListener, nodeBuilder
                        .setProtocol(new ProtocolBuilder().setName(Name.TLS).build()).build(), NODE_ID);
        assertEquals(NetconfClientConfiguration.NetconfClientProtocol.TLS, configuration4.getProtocol());
        assertNull(configuration4.getAuthHandler());
        assertNotNull(configuration4.getSslHandlerFactory());
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
                                          final EventExecutor eventExecutor,
                                          final ScheduledThreadPool keepaliveExecutor,
                                          final ThreadPool processingExecutor,
                                          final SchemaResourceManager schemaRepositoryProvider,
                                          final DataBroker dataBroker, final DOMMountPointService mountPointService,
                                          final AAAEncryptionService encryptionService,
                                          final RpcProviderService rpcProviderService) {
            super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor,
                  processingExecutor, schemaRepositoryProvider, dataBroker,
                  mountPointService, encryptionService, rpcProviderService, BASE_SCHEMAS, null);
        }

        @Override
        public ListenableFuture<Empty> connectNode(final NodeId nodeId, final Node configNode) {
            return Futures.immediateFuture(Empty.value());
        }

        @Override
        public ListenableFuture<Empty> disconnectNode(final NodeId nodeId) {
            return Futures.immediateFuture(Empty.value());
        }
    }

    @Test
    public void hideCredentialsTest() {
        final String userName = "admin";
        final String password = "pa$$word";
        final Node node = new NodeBuilder()
                .addAugmentation(new NetconfNodeBuilder()
                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                    .setPort(new PortNumber(Uint16.valueOf(9999)))
                    .setReconnectOnChangedSchema(true)
                    .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                    .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
                    .setKeepaliveDelay(Uint32.valueOf(1000))
                    .setTcpOnly(false)
                    .setProtocol(new ProtocolBuilder().setName(Name.TLS).build())
                    .setCredentials(new LoginPasswordBuilder()
                        .setUsername(userName)
                        .setPassword(password)
                        .build())
                    .build())
                .setNodeId(NodeId.getDefaultInstance("junos"))
                .build();
        final String transformedNetconfNode = AbstractNetconfTopology.hideCredentials(node);
        assertTrue(transformedNetconfNode.contains("credentials=***"));
        assertFalse(transformedNetconfNode.contains(userName));
        assertFalse(transformedNetconfNode.contains(password));
    }

    @Test
    public void hideNullCredentials() {
        final Node node = new NodeBuilder()
            .setNodeId(new NodeId("id"))
            .addAugmentation(new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setSchemaless(false)
                .setReconnectOnChangedSchema(false)
                .setMaxConnectionAttempts(Uint32.ZERO)
                .setLockDatastore(true)
                .build())
            .build();
        assertNotNull(AbstractNetconfTopology.hideCredentials(node));
    }
}
