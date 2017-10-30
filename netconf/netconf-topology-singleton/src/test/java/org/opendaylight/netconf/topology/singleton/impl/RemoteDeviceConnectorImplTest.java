/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.Futures;
import io.netty.util.concurrent.EventExecutor;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfConnectorDTO;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
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
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import scala.concurrent.duration.Duration;

public class RemoteDeviceConnectorImplTest {

    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final String TOPOLOGY_ID = "testing-topology";
    private static final Timeout TIMEOUT = new Timeout(Duration.create(5, "seconds"));

    @Mock
    private DataBroker dataBroker;

    @Mock
    private RpcProviderRegistry rpcProviderRegistry;

    @Mock
    private ClusterSingletonServiceProvider clusterSingletonServiceProvider;

    @Mock
    private ScheduledThreadPool keepaliveExecutor;

    @Mock
    private ThreadPool processingExecutor;

    @Mock
    private ActorSystem actorSystem;

    @Mock
    private EventExecutor eventExecutor;

    @Mock
    private NetconfClientDispatcher clientDispatcher;

    @Mock
    private DOMMountPointService mountPointService;

    @Mock
    private BindingTransactionChain txChain;

    @Mock
    private WriteTransaction writeTx;

    private NetconfTopologySetup.NetconfTopologySetupBuilder builder;
    private RemoteDeviceId remoteDeviceId;

    @Before
    public void setUp() {
        initMocks(this);

        remoteDeviceId = new RemoteDeviceId(TOPOLOGY_ID,
                new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));

        doReturn(txChain).when(dataBroker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(writeTx).when(txChain).newWriteOnlyTransaction();
        doNothing().when(writeTx).merge(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        doReturn("Some object").when(writeTx).getIdentifier();
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        builder = new NetconfTopologySetup.NetconfTopologySetupBuilder();
        builder.setDataBroker(dataBroker);
        builder.setRpcProviderRegistry(rpcProviderRegistry);
        builder.setClusterSingletonServiceProvider(clusterSingletonServiceProvider);
        builder.setKeepaliveExecutor(keepaliveExecutor);
        builder.setProcessingExecutor(processingExecutor);
        builder.setActorSystem(actorSystem);
        builder.setEventExecutor(eventExecutor);
        builder.setNetconfClientDispatcher(clientDispatcher);
        builder.setTopologyId(TOPOLOGY_ID);
    }

    @Test
    public void testStopRemoteDeviceConnection() {
        final Credentials credentials = new LoginPasswordBuilder()
                .setPassword("admin").setUsername("admin").build();
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setSchemaless(false)
                .setTcpOnly(false)
                .setCredentials(credentials)
                .build();
        final Node node = new NodeBuilder().setNodeId(NODE_ID).addAugmentation(NetconfNode.class, netconfNode).build();

        builder.setNode(node);


        final NetconfDeviceCommunicator communicator = mock(NetconfDeviceCommunicator.class);
        final RemoteDeviceHandler salFacade = mock(RemoteDeviceHandler.class);

        final TestingRemoteDeviceConnectorImpl remoteDeviceConnection =
                new TestingRemoteDeviceConnectorImpl(builder.build(), remoteDeviceId, communicator, salFacade, TIMEOUT,
                        mountPointService);

        final ActorRef masterRef = mock(ActorRef.class);

        remoteDeviceConnection.startRemoteDeviceConnection(masterRef);

        remoteDeviceConnection.stopRemoteDeviceConnection();

        verify(communicator, times(1)).close();
        verify(salFacade, times(1)).close();

    }

    @Test
    public void testMasterSalFacade() throws UnknownHostException {
        final ExecutorService executorService = mock(ExecutorService.class);
        doReturn(executorService).when(processingExecutor).getExecutor();

        final Credentials credentials = new LoginPasswordBuilder()
                .setPassword("admin").setUsername("admin").build();
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setSchemaless(false)
                .setTcpOnly(false)
                .setCredentials(credentials)
                .build();

        final Node node = new NodeBuilder().setNodeId(NODE_ID).addAugmentation(NetconfNode.class, netconfNode).build();
        builder.setSchemaResourceDTO(NetconfTopologyUtils.setupSchemaCacheDTO(node));
        final RemoteDeviceConnectorImpl remoteDeviceConnection =
                new RemoteDeviceConnectorImpl(builder.build(), remoteDeviceId, TIMEOUT, mountPointService);

        final ActorRef masterRef = mock(ActorRef.class);

        final NetconfConnectorDTO connectorDTO =
                remoteDeviceConnection.createDeviceCommunicator(NODE_ID, netconfNode, masterRef);

        assertTrue(connectorDTO.getFacade() instanceof MasterSalFacade);
    }

    @Test
    public void testKeapAliveFacade() {
        final ExecutorService executorService = mock(ExecutorService.class);
        doReturn(executorService).when(processingExecutor).getExecutor();

        final Credentials credentials = new LoginPasswordBuilder()
                .setPassword("admin").setUsername("admin").build();
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(9999))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(1000L)
                .setBetweenAttemptsTimeoutMillis(100)
                .setSchemaless(false)
                .setTcpOnly(false)
                .setCredentials(credentials)
                .setKeepaliveDelay(1L)
                .build();

        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId(TOPOLOGY_ID,
                new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));
        final Node node = new NodeBuilder().setNodeId(NODE_ID).addAugmentation(NetconfNode.class, netconfNode).build();
        builder.setSchemaResourceDTO(NetconfTopologyUtils.setupSchemaCacheDTO(node));

        final RemoteDeviceConnectorImpl remoteDeviceConnection =
                new RemoteDeviceConnectorImpl(builder.build(), remoteDeviceId, TIMEOUT, mountPointService);

        final ActorRef masterRef = mock(ActorRef.class);

        final NetconfConnectorDTO connectorDTO =
                remoteDeviceConnection.createDeviceCommunicator(NODE_ID, netconfNode, masterRef);

        assertTrue(connectorDTO.getFacade() instanceof KeepaliveSalFacade);
    }

    @Test
    public void testGetClientConfig() {
        final NetconfClientSessionListener listener = mock(NetconfClientSessionListener.class);
        final Host host = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
        final PortNumber portNumber = new PortNumber(9999);
        final NetconfNode testingNode = new NetconfNodeBuilder()
                .setConnectionTimeoutMillis(1000L)
                .setDefaultRequestTimeoutMillis(2000L)
                .setHost(host)
                .setPort(portNumber)
                .setCredentials(new LoginPasswordBuilder()
                        .setUsername("testuser")
                        .setPassword("testpassword").build())
                .setTcpOnly(true)
                .build();

        final RemoteDeviceConnectorImpl remoteDeviceConnection =
                new RemoteDeviceConnectorImpl(builder.build(), remoteDeviceId, TIMEOUT, mountPointService);

        final NetconfReconnectingClientConfiguration defaultClientConfig =
                remoteDeviceConnection.getClientConfig(listener, testingNode);

        assertEquals(defaultClientConfig.getConnectionTimeoutMillis().longValue(), 1000L);
        assertEquals(defaultClientConfig.getAddress(), new InetSocketAddress(InetAddresses.forString("127.0.0.1"),
            9999));
        assertSame(defaultClientConfig.getSessionListener(), listener);
        assertEquals(defaultClientConfig.getAuthHandler().getUsername(), "testuser");
        assertEquals(defaultClientConfig.getProtocol(), NetconfClientConfiguration.NetconfClientProtocol.TCP);
    }
}
