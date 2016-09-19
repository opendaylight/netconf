/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.SucceededFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.impl.ConnectionStatusListenerRegistration;
import org.opendaylight.netconf.topology.schema.SchemaRepositoryProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.DataContainer;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;

public class AbstractNetconfTopologyTest {

    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final String TOPOLOGY_ID = "testing-topology";

    @Mock
    private Broker mockedDataBroker;

    @Mock
    private NetconfClientDispatcher mockedClientDispatcher;

    @Mock
    private BindingAwareBroker mockedBindingAwareBroker;

    @Mock
    private EventExecutor mockedEventExecutor;

    @Mock
    private ScheduledThreadPool mockedKeepaliveExecutor;

    @Mock
    private ThreadPool mockedProcessingExecutor;

    @Mock
    private SchemaRepositoryProvider mockedSchemaRepositoryProvider;


    private TestingAbstractNetconfTopology topology;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mockedSchemaRepositoryProvider.getSharedSchemaRepository()).thenReturn(new SharedSchemaRepository("testingSharedSchemaRepo"));
        when(mockedProcessingExecutor.getExecutor()).thenReturn(MoreExecutors.newDirectExecutorService());
        Future<Void> future = new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
        when(mockedClientDispatcher.createReconnectingClient(any(NetconfReconnectingClientConfiguration.class))).thenReturn(future);

        topology = new TestingAbstractNetconfTopology(TOPOLOGY_ID, mockedClientDispatcher, mockedBindingAwareBroker,
                mockedDataBroker, mockedEventExecutor, mockedKeepaliveExecutor, mockedProcessingExecutor, mockedSchemaRepositoryProvider);
    }

    @Test
    public void testCreateSalFacade() {
        NetconfNode testingNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setSchemaless(false)
                .build();

        AbstractNetconfTopology.NetconfConnectorDTO connectorDTO = topology.createDeviceCommunicator(NODE_ID, testingNode);
        assertSame(connectorDTO.getFacade(), topology.getFacade());
    }

    @Test
    public void testCreateKeepAliveSalFacade() {
        NetconfNode testingNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setKeepaliveDelay(1L)
                .setSchemaless(false)
                .build();

        AbstractNetconfTopology.NetconfConnectorDTO connectorDTO = topology.createDeviceCommunicator(NODE_ID, testingNode);
        assertTrue(connectorDTO.getFacade() instanceof KeepaliveSalFacade);
    }

    @Test
    public void testSetupSchemaResourceDTO() {
        NetconfNode testingNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setKeepaliveDelay(1000L).build();

        NetconfDevice.SchemaResourcesDTO resultDTO = topology.setupSchemaCacheDTO(NODE_ID, testingNode);
        SharedSchemaRepository repo = (SharedSchemaRepository) resultDTO.getSchemaRegistry();
        assertEquals(repo.getIdentifier(), "sal-netconf-connector");

        testingNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setKeepaliveDelay(1000L)
                .setSchemaCacheDirectory("test-directory")
                .build();

        resultDTO = topology.setupSchemaCacheDTO(NODE_ID, testingNode);
        repo = (SharedSchemaRepository) resultDTO.getSchemaRegistry();
        assertEquals(repo.getIdentifier(), "test-directory");
    }

    @Test
    public void testGetClientConfig() throws UnknownHostException {
        NetconfClientSessionListener listener = mock(NetconfClientSessionListener.class);
        Host host = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
        PortNumber portNumber = new PortNumber(9999);
        NetconfNode testingNode = new NetconfNodeBuilder()
                .setConnectionTimeoutMillis(1000L)
                .setDefaultRequestTimeoutMillis(2000L)
                .setHost(host)
                .setPort(portNumber)
                .setCredentials(new LoginPasswordBuilder().setUsername("testuser").setPassword("testpassword").build())
                .setTcpOnly(true)
                .build();
        NetconfReconnectingClientConfiguration defaultClientConfig = topology.getClientConfig(listener, testingNode);

        assertEquals(defaultClientConfig.getConnectionTimeoutMillis().longValue(), 1000L);
        assertEquals(defaultClientConfig.getAddress(), new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 9999));
        assertSame(defaultClientConfig.getSessionListener(), listener);
        assertEquals(defaultClientConfig.getAuthHandler().getUsername(), "testuser");
        assertEquals(defaultClientConfig.getProtocol(), NetconfClientConfiguration.NetconfClientProtocol.TCP);
    }

    @Test
    public void testGetClientConfigNotSupportedCredentialsFail() {
        NetconfClientSessionListener listener = mock(NetconfClientSessionListener.class);
        Host host = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
        PortNumber portNumber = new PortNumber(9999);

        Credentials notSupportedCredentials = new Credentials() {
            @Override
            public Class<? extends DataContainer> getImplementedInterface() {
                return Credentials.class;
            }
        };

        NetconfNode testingNode = new NetconfNodeBuilder()
                .setConnectionTimeoutMillis(1000L)
                .setDefaultRequestTimeoutMillis(2000L)
                .setHost(host)
                .setPort(portNumber)
                .setCredentials(notSupportedCredentials)
                .setTcpOnly(true)
                .build();
        try {
            topology.getClientConfig(listener, testingNode);
            fail("Exception expected here.");
        } catch(Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }

    @Test
    public void testConnectNode() {
        NetconfNode testingNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setKeepaliveDelay(1000L)
                .setTcpOnly(true)
                .setSchemaless(false)
                .setCredentials(new LoginPasswordBuilder().setUsername("testuser").setPassword("testpassword").build())
                .build();
        Node nd = mock(Node.class);
        when(nd.getAugmentation(NetconfNode.class)).thenReturn(testingNode);
        topology.connectNode(NODE_ID, nd);
        assertTrue(topology.activeConnectors.containsKey(NODE_ID));
    }

    @Test
    public void testDisconnectNode() {
        NetconfNode testingNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setKeepaliveDelay(1000L)
                .setTcpOnly(true)
                .setSchemaless(false)
                .setCredentials(new LoginPasswordBuilder().setUsername("testuser").setPassword("testpassword").build())
                .build();
        Node nd = mock(Node.class);
        when(nd.getAugmentation(NetconfNode.class)).thenReturn(testingNode);
        topology.connectNode(NODE_ID, nd);
        assertTrue(topology.activeConnectors.containsKey(NODE_ID));
        assertTrue(topology.disconnectNode(NODE_ID).isDone());
        assertTrue(!topology.activeConnectors.containsKey(NODE_ID));
        verify(topology.getFacade()).close();
    }

    @Test
    public void testDisconnectNotConnectedNode() throws ExecutionException, InterruptedException {
        ListenableFuture disconnectFuture = topology.disconnectNode(NODE_ID);
        assertTrue(disconnectFuture.isDone());
        try {
            disconnectFuture.get();
            fail("Exception expected!");
        } catch(Exception e) {
            assertTrue(e instanceof ExecutionException);
            assertTrue(e.getCause() instanceof  IllegalStateException);
        }
    }

    public static class TestingAbstractNetconfTopology extends AbstractNetconfTopology {

        private RemoteDeviceHandler salFacade;

        protected TestingAbstractNetconfTopology(String topologyId, NetconfClientDispatcher clientDispatcher,
                                                 BindingAwareBroker bindingAwareBroker, Broker domBroker,
                                                 EventExecutor eventExecutor, ScheduledThreadPool keepaliveExecutor,
                                                 ThreadPool processingExecutor,
                                                 SchemaRepositoryProvider schemaRepositoryProvider) {
            super(topologyId, clientDispatcher, bindingAwareBroker, domBroker, eventExecutor, keepaliveExecutor, processingExecutor, schemaRepositoryProvider);
            salFacade = mock(RemoteDeviceHandler.class);
        }

        public RemoteDeviceHandler<NetconfSessionPreferences> getFacade() {
            return salFacade;
        }

        @Override
        public void onSessionInitiated(BindingAwareBroker.ProviderContext session) {

        }

        @Override
        protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(RemoteDeviceId id, Broker domBroker, BindingAwareBroker bindingBroker) {
            return salFacade;
        }

        @Override
        public ConnectionStatusListenerRegistration registerConnectionStatusListener(NodeId node, RemoteDeviceHandler<NetconfSessionPreferences> listener) {
            return null;
        }

        @Override
        public void registerMountPoint(ActorContext context, NodeId nodeId) {

        }

        @Override
        public void registerMountPoint(ActorContext context, NodeId nodeId, ActorRef masterRef) {

        }

        @Override
        public void unregisterMountPoint(NodeId nodeId) {

        }
    }
}
