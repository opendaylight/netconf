/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.google.common.net.InetAddresses;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.EventExecutor;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;


@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfNodeHandlerTest {

    private NetconfClientDispatcherImpl clientDispatcher;
    @Mock
    private RemoteDeviceHandler delegate;
    @Mock
    private EventExecutor eventExecutor;
    @Mock
    private ScheduledExecutorService executorService;
    @Mock
    private SchemaResourceManager schemaManager;
    @Mock
    private Executor processingExecutor;
    @Mock
    private DeviceActionFactory deviceActionFactory;
    @Mock
    private SslHandlerFactoryProvider sslHandlerFactoryProvider;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private CredentialProvider credentialProvider;
    private BaseNetconfSchemas baseSchemas;
    private NetconfNode node;
    private NetconfNodeHandler handler;


    private final RemoteDeviceId deviceId = new RemoteDeviceId("netconf-topology",
        new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));

    private NodeId nodeId = new NodeId("testing-node");
    private DefaultNetconfClientConfigurationBuilderFactory builderFactory;


    @Before
    public void setUp() throws YangParserException {
        //builderFactory clientDispatcher and init
        builderFactory = new DefaultNetconfClientConfigurationBuilderFactory(encryptionService, credentialProvider,
            sslHandlerFactoryProvider);

        final var hashedWheelTimer = new HashedWheelTimer();
        final var nettyGroup = new NioEventLoopGroup();
        clientDispatcher = new NetconfClientDispatcherImpl(nettyGroup, nettyGroup, hashedWheelTimer);

        baseSchemas = new DefaultBaseNetconfSchemas(new DefaultYangParserFactory());

        //create node
        final var nodeBuilder = new NetconfNodeBuilder()
            .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
            .setPort(new PortNumber(Uint16.valueOf(9999)))
            .setReconnectOnChangedSchema(true)
            .setSchemaless(true)
            .setTcpOnly(true)
            .setSleepFactor(Decimal64.valueOf("1.5"))
            .setConcurrentRpcLimit(Uint16.valueOf(1))
            .setMaxConnectionAttempts(Uint32.ZERO)
            .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
            .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
            .setKeepaliveDelay(Uint32.valueOf(1000))
            .setConnectionTimeoutMillis(Uint32.valueOf(1000))
            .setCredentials(new LoginPasswordBuilder().setUsername("testuser").setPassword("testpassword").build());

        node = nodeBuilder.build();

        // Instantiate the handler
        handler = new NetconfNodeHandler(clientDispatcher, eventExecutor, executorService, baseSchemas,
            schemaManager, processingExecutor, builderFactory, deviceActionFactory, delegate,
            deviceId, nodeId, node, null);

//        handler.connect();

    }

    /**
     * Test device reconnection logic with unlimited reconnections (set to 0).
     * Test that device is in {@link ConnectionStatus.Connecting} state when it's unable to connect,
     * and we have NOT yet reached the number of reconnection attempts.
     */
    @Test
    public void testDeviceConnecting() {
        handler.connect();
        // expected node to have some connection status after handler.connect, not sure if that even be changed on this
        // instance
        // tried several approaches of getting connection status, but no result...
//        node.getConnectionStatus();
        verify(delegate, atLeastOnce()).onDeviceDisconnected();
        // IDK try to verify state "connecting"
    }

    /**
     * Test device reconnection logic with limited reconnection.
     * Test that device is in {@link ConnectionStatus.UnableToConnect} state when it's unable to connect,
     * and we have reached out the number of reconnection attempts.
     */
    @Test
    public void testDeviceUnableToConnect() {

    }

    /**
     * Test device successful reconnection.
     * Test that device IS connected successfully after series of unsuccessful tries by reconnection logic when number
     * of reconnection attempts is NOT exceeded.
     */
    @Test
    public void testDeviceConnected() {

    }

    /**
     * Test device unsuccessful reconnection.
     * Test that device is NOT connected after series of unsuccessful tries by reconnection logic when number
     * of reconnection attempts IS exceeded.
     */
    @Test
    public void testDeviceUnConnected() {

    }

}
