/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.netty.handler.ssl.SslContext;
import java.security.KeyPairGenerator;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslContextFactory;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.transport.crypto.KeyPairParser;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.Inline;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.KeyAuthBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.key.auth.KeyBasedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNodeBuilder;
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
    private SslContextFactoryProvider sslContextFactoryProvider;
    @Mock
    private SslContextFactory sslContextFactory;
    @Mock
    private SslContext sslContext;
    @Mock
    private CredentialProvider credentialProvider;

    private NetconfNodeBuilder nodeBuilder;
    private NetconfClientConfigurationBuilderFactoryImpl factory;

    @BeforeEach
    void beforeEach() {
        nodeBuilder = new NetconfNodeBuilder()
            .setHost(HOST).setPort(PORT)
            .setReconnectOnChangedSchema(true)
            .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
            .setMinBackoffMillis(Uint16.valueOf(100))
            .setKeepaliveDelay(Uint32.valueOf(1000))
            .setCredentials(new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername("test-user")
                    .setPassword("test-password")
                    .build())
                .build())
            .setMaxConnectionAttempts(Uint32.ZERO)
            .setBackoffMultiplier(Decimal64.valueOf("1.5"))
            .setConnectionTimeoutMillis(Uint32.valueOf(20000));
        factory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, sslContextFactoryProvider);
    }

    private void assertConfig(final NetconfClientConfiguration config) {
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
    void testSshKeyBased() {
        final var config = createConfig(nodeBuilder.setTcpOnly(false)
            .setCredentials(new KeyAuthBuilder()
                .setKeyBased(new KeyBasedBuilder()
                    .setUsername("test-user")
                    .setKeyId("test-key")
                    .build())
                .build())
            .build());
        assertConfig(config);
        assertEquals(NetconfClientProtocol.SSH, config.getProtocol());

        final var clientIdentity = config.getSshParameters().getClientIdentity();
        assertEquals("test-user", clientIdentity.getUsername());
        assertNull(clientIdentity.getPassword());
        final var centralKeystore = assertInstanceOf(CentralKeystore.class, clientIdentity.getPublicKey()
            .getInlineOrKeystore());
        assertEquals("test-key", centralKeystore.getCentralKeystoreReference());
    }

    @Test
    void testSshKeyBasedWithoutKeyId() {
        // NetconfNodeHandler reports an IllegalArgumentException from the factory as a device failure
        final var node = nodeBuilder.setTcpOnly(false)
            .setCredentials(new KeyAuthBuilder()
                .setKeyBased(new KeyBasedBuilder()
                    .setUsername("test-user")
                    .build())
                .build())
            .build();
        final var ex = assertThrows(IllegalArgumentException.class, () -> createConfig(node));
        assertEquals("Key-based credentials are missing key-id", ex.getMessage());
    }

    // The tests of the deprecated CredentialProvider path are removed together with the deprecated constructor
    @Test
    @Deprecated(since = "12.0.3", forRemoval = true)
    void testSshKeyBasedWithCredentialProvider() throws Exception {
        final var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        final var keyPair = generator.generateKeyPair();
        doReturn(keyPair).when(credentialProvider).credentialForId("test-key");

        // The deprecated constructor resolves the key through the CredentialProvider and passes it inline, so it works
        // without a central keystore
        factory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider,
            sslContextFactoryProvider);
        final var clientIdentity = createConfig(keyBasedNode()).getSshParameters().getClientIdentity();
        assertEquals("test-user", clientIdentity.getUsername());
        final var inline = assertInstanceOf(Inline.class, clientIdentity.getPublicKey().getInlineOrKeystore());
        final var definition = inline.getInlineDefinition();
        assertEquals(SubjectPublicKeyInfoFormat.VALUE, definition.getPublicKeyFormat());
        assertArrayEquals(keyPair.getPublic().getEncoded(), definition.getPublicKey());
        assertEquals(RsaPrivateKeyFormat.VALUE, definition.getPrivateKeyFormat());
        final var privateKey = assertInstanceOf(CleartextPrivateKey.class, definition.getPrivateKeyType());
        assertArrayEquals(keyPair.getPrivate().getEncoded(), privateKey.getCleartextPrivateKey());

        // transport-ssh reads the inline key with KeyPairParser, which also checks that the two keys belong together
        final var parsedKeyPair = KeyPairParser.parseKeyPair(definition);
        assertArrayEquals(keyPair.getPublic().getEncoded(), parsedKeyPair.getPublic().getEncoded());
    }

    @Test
    @Deprecated(since = "12.0.3", forRemoval = true)
    void testSshKeyBasedWithCredentialProviderMissingKey() {
        doReturn(null).when(credentialProvider).credentialForId("test-key");

        factory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider,
            sslContextFactoryProvider);
        final var node = keyBasedNode();
        final var ex = assertThrows(IllegalArgumentException.class, () -> createConfig(node));
        assertEquals("No keypair found with keyId=test-key", ex.getMessage());
    }

    @Test
    @Deprecated(since = "12.0.3", forRemoval = true)
    void testSshKeyBasedWithCredentialProviderUnsupportedKey() throws Exception {
        // transport-ssh parses only RSA and EC keys
        final var generator = KeyPairGenerator.getInstance("DSA");
        generator.initialize(2048);
        doReturn(generator.generateKeyPair()).when(credentialProvider).credentialForId("test-key");

        factory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider,
            sslContextFactoryProvider);
        final var node = keyBasedNode();
        final var ex = assertThrows(IllegalArgumentException.class, () -> createConfig(node));
        assertEquals("Unsupported DSA key with keyId=test-key", ex.getMessage());
    }

    @Deprecated(since = "12.0.3", forRemoval = true)
    private NetconfNode keyBasedNode() {
        return nodeBuilder.setTcpOnly(false)
            .setCredentials(new KeyAuthBuilder()
                .setKeyBased(new KeyBasedBuilder()
                    .setUsername("test-user")
                    .setKeyId("test-key")
                    .build())
                .build())
            .build();
    }

    @Test
    void testTcp() {
        final var config = createConfig(nodeBuilder.setTcpOnly(true).build());
        assertConfig(config);
        assertEquals(NetconfClientProtocol.TCP, config.getProtocol());
    }

    @Test
    void testTls() {
        doReturn(sslContextFactory).when(sslContextFactoryProvider).getSslContextFactory(any());
        doReturn(sslContext).when(sslContextFactory).createSslContext();
        final var config = createConfig(
            nodeBuilder.setTcpOnly(false).setProtocol(new ProtocolBuilder().setName(Name.TLS).build()).build());
        assertConfig(config);
        assertEquals(NetconfClientProtocol.TLS, config.getProtocol());
        assertNotNull(config.getSslHandlerFactory());
    }

    @Test
    void testTlsWithKeyId() {
        final var keyIds = Set.of("key-id1", "key-id2");
        doReturn(sslContextFactory).when(sslContextFactoryProvider).getSslContextFactory(any());
        doReturn(sslContext).when(sslContextFactory).createSslContext(eq(keyIds));
        final var config = createConfig(
            nodeBuilder.setTcpOnly(false).setProtocol(
                new ProtocolBuilder().setName(Name.TLS).setKeyId(keyIds).build()).build());
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
        return factory.createClientConfigurationBuilder(NODE_ID, netconfNode,
                AbstractNetconfTopology.defaultSshParams())
            .withSessionListener(sessionListener)
            .build();
    }
}
