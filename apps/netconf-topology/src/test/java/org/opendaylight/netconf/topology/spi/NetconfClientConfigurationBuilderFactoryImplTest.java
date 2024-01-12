/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240110.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240110.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240110.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240110.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class NetconfClientConfigurationBuilderFactoryImplTest {
    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final Host HOST = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
    private static final PortNumber PORT = new PortNumber(Uint16.valueOf(9999));

    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private CredentialProvider credentialProvider;
    @Mock
    private SslHandlerFactoryProvider sslHandlerFactoryProvider;

    private NetconfNodeBuilder nodeBuilder;
    private NetconfClientConfigurationBuilderFactoryImpl factory;

    @BeforeEach
    void beforeEach() {
        nodeBuilder = new NetconfNodeBuilder()
            .setHost(HOST).setPort(PORT)
            .setReconnectOnChangedSchema(true)
            .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
            .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
            .setKeepaliveDelay(Uint32.valueOf(1000))
            .setCredentials(new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername("test-user")
                    .setPassword("test-password")
                    .build())
                .build())
            .setMaxConnectionAttempts(Uint32.ZERO)
            .setSleepFactor(Decimal64.valueOf("1.5"))
            .setConnectionTimeoutMillis(Uint32.valueOf(20000));
        factory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider,
            sslHandlerFactoryProvider);
    }

    private void assertConfig(NetconfClientConfiguration config) {
        assertNotNull(config);
        assertNotNull(config.getTcpParameters());
        assertEquals(HOST, config.getTcpParameters().getRemoteAddress());
        assertEquals(PORT, config.getTcpParameters().getRemotePort());
        assertSame(sessionListener, config.getSessionListener());
    }

    @Test
    void testDefault() {
        final var config = createConfig(nodeBuilder.setTcpOnly(false).build());
        assertConfig(config);
        assertEquals(NetconfClientProtocol.SSH, config.getProtocol());
        assertNotNull(config.getSshParameters());
    }

    @Test
    void testSsh() {
        final var config = createConfig(
            nodeBuilder.setTcpOnly(false).setProtocol(new ProtocolBuilder().setName(Name.SSH).build()).build());
        assertConfig(config);
        assertEquals(NetconfClientProtocol.SSH, config.getProtocol());
        assertNotNull(config.getSshParameters());
    }

    @Test
    void testTcp() {
        final var config = createConfig(nodeBuilder.setTcpOnly(true).build());
        assertConfig(config);
        assertEquals(NetconfClientProtocol.TCP, config.getProtocol());
    }

    @Test
    void testTls() {
        final var config = createConfig(
            nodeBuilder.setTcpOnly(false).setProtocol(new ProtocolBuilder().setName(Name.TLS).build()).build());
        assertConfig(config);
        assertEquals(NetconfClientProtocol.TLS, config.getProtocol());
        assertNotNull(config.getSslHandlerFactory());
    }

    @Test
    void noPort() {
        assertThrows(NoSuchElementException.class, () -> createConfig(nodeBuilder.setPort(null).build()));
    }

    @Test
    void noHost() {
        assertThrows(NoSuchElementException.class, () -> createConfig(nodeBuilder.setHost(null).build()));
    }

    private NetconfClientConfiguration createConfig(final NetconfNode netconfNode) {
        return factory.createClientConfigurationBuilder(NODE_ID, netconfNode)
            .withSessionListener(sessionListener)
            .build();
    }
}
