/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doReturn;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class DefaultNetconfClientConfigurationBuilderFactoryTest {
    private static final NodeId NODE_ID = new NodeId("testing-node");

    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private CredentialProvider credentialProvider;
    @Mock
    private SslHandlerFactoryProvider sslHandlerFactoryProvider;
    @Mock
    private SslHandlerFactory sslHandlerFactory;

    private final NetconfNodeBuilder nodeBuilder = new NetconfNodeBuilder()
        .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
        .setPort(new PortNumber(Uint16.valueOf(9999)))
        .setReconnectOnChangedSchema(true)
        .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
        .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
        .setKeepaliveDelay(Uint32.valueOf(1000))
        .setCredentials(new LoginPasswordBuilder().setUsername("testuser").setPassword("testpassword").build())
        .setMaxConnectionAttempts(Uint32.ZERO)
        .setSleepFactor(Decimal64.valueOf("1.5"))
        .setConnectionTimeoutMillis(Uint32.valueOf(20000));

    private DefaultNetconfClientConfigurationBuilderFactory factory;

    @Before
    public void before() {
        doReturn(sslHandlerFactory).when(sslHandlerFactoryProvider).getSslHandlerFactory(null);

        factory = new DefaultNetconfClientConfigurationBuilderFactory(encryptionService, credentialProvider,
            sslHandlerFactoryProvider);
    }

    @Test
    public void testDefault() {
        final var config = createConfig(nodeBuilder.setTcpOnly(false).build());
        assertEquals(NetconfClientProtocol.SSH, config.getProtocol());
        assertNotNull(config.getAuthHandler());
        assertNull(config.getSslHandlerFactory());
    }

    @Test
    public void testSsh() {
        final var config = createConfig(
            nodeBuilder.setTcpOnly(false).setProtocol(new ProtocolBuilder().setName(Name.SSH).build()).build());
        assertEquals(NetconfClientProtocol.SSH, config.getProtocol());
        assertNotNull(config.getAuthHandler());
        assertNull(config.getSslHandlerFactory());
    }

    @Test
    public void testTcp() {
        final var config = createConfig(nodeBuilder.setTcpOnly(true).build());
        assertEquals(NetconfClientProtocol.TCP, config.getProtocol());
        assertNotNull(config.getAuthHandler());
        assertNull(config.getSslHandlerFactory());
    }

    @Test
    public void testTls() {
        final var config = createConfig(
            nodeBuilder.setTcpOnly(false).setProtocol(new ProtocolBuilder().setName(Name.TLS).build()).build());
        assertEquals(NetconfClientProtocol.TLS, config.getProtocol());
        assertNull(config.getAuthHandler());
        assertSame(sslHandlerFactory, config.getSslHandlerFactory());
    }

    private NetconfClientConfiguration createConfig(final NetconfNode netconfNode) {
        return factory.createClientConfigurationBuilder(NODE_ID, netconfNode)
            .withSessionListener(sessionListener)
            .build();
    }
}
