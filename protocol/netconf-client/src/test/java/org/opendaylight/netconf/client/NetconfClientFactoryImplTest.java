/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.netty.handler.ssl.SslContextBuilder;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.PasswordIdentityProvider;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.ssh.ServerFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tls.FixedSslHandlerFactory;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.InlineBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.inline.InlineDefinitionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.tls.tls.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.listen.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.ServerIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.ServerIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.client.authentication.UsersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.client.authentication.users.UserBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.server.identity.HostKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh.server.grouping.server.identity.host.key.host.key.type.PublicKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev241010.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBindBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
class NetconfClientFactoryImplTest {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0rd";
    private static final String RSA = "RSA";
    private static final char[] EMPTY_SECRET = new char[0];

    private static SSHTransportStackFactory SERVER_FACTORY;
    private static DefaultNetconfTimer TIMER;

    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private TransportChannelListener<TransportChannel> serverTransportListener;
    @Mock
    private SshServerGrouping sshServerParams;

    private NetconfClientFactory factory;
    private TcpServerGrouping tcpServerParams;
    private TcpClientGrouping tcpClientParams;

    @BeforeAll
    static void beforeAll() {
        SERVER_FACTORY = new SSHTransportStackFactory("server", 0);
        TIMER = new DefaultNetconfTimer();
    }

    @AfterAll
    static void afterAll() {
        SERVER_FACTORY.close();
        TIMER.close();
    }

    @BeforeEach
    void beforeEach() throws Exception {
        factory = new NetconfClientFactoryImpl(TIMER);
        doNothing().when(serverTransportListener).onTransportChannelEstablished(any());

        // create temp socket to get available port for test
        final int localPort;
        try (var socket = new ServerSocket(0)) {
            localPort = socket.getLocalPort();
        }

        final var address = IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress());
        final var port = new PortNumber(Uint16.valueOf(localPort));

        tcpServerParams = new TcpServerParametersBuilder()
            .setLocalBind(BindingMap.of(new LocalBindBuilder().setLocalAddress(address).setLocalPort(port).build()))
            .build();
        tcpClientParams = new TcpClientParametersBuilder()
            .setRemoteAddress(new Host(address))
            .setRemotePort(port)
            .build();
    }

    @AfterEach
    void afterEach() throws Exception {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void tcpClient() throws Exception {
        final var server = TCPServer.listen(serverTransportListener,
            SERVER_FACTORY.newServerBootstrap(), tcpServerParams).get(1, TimeUnit.SECONDS);
        try {
            final var clientConfig = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
                .withTcpParameters(tcpClientParams).withSessionListener(sessionListener).build();
            assertNotNull(factory.createClient(clientConfig));
            verify(serverTransportListener, timeout(1000L))
                .onTransportChannelEstablished(any(TransportChannel.class));
        } finally {
            server.shutdown().get(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void tlsClient() throws Exception {
        final var keyStore = buildKeystoreWithGeneratedCertificate();
        final var keyMgr = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyMgr.init(keyStore, EMPTY_SECRET);
        final var trustMgr = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustMgr.init(keyStore);
        final var serverContext = SslContextBuilder.forServer(keyMgr).trustManager(trustMgr).build();
        final var clientContext = SslContextBuilder.forClient().keyManager(keyMgr).trustManager(trustMgr).build();

        final var server = TLSServer.listen(serverTransportListener, SERVER_FACTORY.newServerBootstrap(),
            tcpServerParams, new FixedSslHandlerFactory(serverContext)).get(1, TimeUnit.SECONDS);
        try {
            final var clientConfig = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS)
                .withTcpParameters(tcpClientParams)
                .withSslHandlerFactory(new FixedSslHandlerFactory(clientContext))
                .withSessionListener(sessionListener).build();
            assertNotNull(factory.createClient(clientConfig));
            verify(serverTransportListener, timeout(1000L))
                .onTransportChannelEstablished(any(TransportChannel.class));
        } finally {
            server.shutdown().get(1, TimeUnit.SECONDS);
        }
    }

    private static KeyStore buildKeystoreWithGeneratedCertificate() throws Exception {
        // key pair
        final var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), new SecureRandom());
        final var keyPair = keyPairGenerator.generateKeyPair();
        // certificate
        final var now = Instant.now();
        final var contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        final var x500Name = new X500Name("CN=TestCertificate");
        final var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now), Date.from(now.plus(Duration.ofDays(365))),
            x500Name,
            keyPair.getPublic());
        final var certificate = new JcaX509CertificateConverter()
            .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
        // keystore with certificate and key
        final var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("cert", certificate);
        keyStore.setKeyEntry("key", keyPair.getPrivate(), EMPTY_SECRET, new Certificate[]{certificate});
        return keyStore;
    }

    @Test
    void sshClient() throws Exception {
        doReturn(buildSshServerIdentity()).when(sshServerParams).getServerIdentity();
        doReturn(buildSshClientAuth()).when(sshServerParams).getClientAuthentication();
        doReturn(null).when(sshServerParams).getTransportParams();
        doReturn(null).when(sshServerParams).getKeepalives();

        final var server = SERVER_FACTORY.listenServer("netconf", serverTransportListener, tcpServerParams,
            sshServerParams).get(10, TimeUnit.SECONDS);

        try {
            final var clientConfig = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withTcpParameters(tcpClientParams)
                .withSshParameters(new SshClientParametersBuilder()
                    .setClientIdentity(new ClientIdentityBuilder().setUsername(USERNAME)
                        .setPassword(new PasswordBuilder().setPasswordType(
                            new CleartextPasswordBuilder().setCleartextPassword(PASSWORD)
                                .build()).build()).build())
                    .build())
                .withSessionListener(sessionListener)
                .withConnectionTimeoutMillis(10_000)
                .build();
            assertNotNull(factory.createClient(clientConfig));
            verify(serverTransportListener, timeout(10_000L))
                .onTransportChannelEstablished(any(TransportChannel.class));
        } finally {
            server.shutdown().get(1, TimeUnit.SECONDS);
        }
    }

    private static ServerIdentity buildSshServerIdentity() throws Exception {
        final var keyPair = KeyPairGenerator.getInstance(RSA).generateKeyPair();
        final var inlineDef = new InlineDefinitionBuilder()
            .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
            .setPublicKey(keyPair.getPublic().getEncoded())
            .setPrivateKeyFormat(RsaPrivateKeyFormat.VALUE)
            .setPrivateKeyType(
                new CleartextPrivateKeyBuilder().setCleartextPrivateKey(
                    keyPair.getPrivate().getEncoded()
                ).build()
            ).build();
        final var inline = new InlineBuilder().setInlineDefinition(inlineDef).build();
        final var publicKey = new PublicKeyBuilder().setPublicKey(
            new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010
                .ssh.server.grouping.server.identity.host.key.host.key.type._public.key
                .PublicKeyBuilder().setInlineOrKeystore(inline).build()
        ).build();
        return new ServerIdentityBuilder().setHostKey(
            List.of(new HostKeyBuilder().setName("test-name").setHostKeyType(publicKey).build())
        ).build();
    }

    private static ClientAuthentication buildSshClientAuth() {
        final var user = new UserBuilder().setName(USERNAME)
            .setPassword(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.ssh
                .server.grouping.client.authentication.users.user.PasswordBuilder()
                .setHashedPassword(new CryptHash("$0$" + PASSWORD))
                .build())
            .build();
        return new ClientAuthenticationBuilder().setUsers(
            new UsersBuilder().setUser(Map.of(user.key(), user)).build()
        ).build();
    }

    @Test
    void sshClientWithConfigurator() throws Exception {
        final ServerFactoryManagerConfigurator serverConfigurator = factoryManager -> {
            factoryManager.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
            factoryManager.setPasswordAuthenticator(
                (usr, psw, session) -> USERNAME.equals(usr) && PASSWORD.equals(psw));
            factoryManager.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        };
        final var clientConfigurator = new ClientFactoryManagerConfigurator() {
            @Override
            protected void configureClientFactoryManager(final ClientFactoryManager factoryManager) {
                factoryManager.setPasswordIdentityProvider(PasswordIdentityProvider.wrapPasswords(PASSWORD));
                factoryManager.setUserAuthFactories(List.of(
                    new org.opendaylight.netconf.shaded.sshd.client.auth.password.UserAuthPasswordFactory()));
            }
        };

        final var server = SERVER_FACTORY.listenServer("netconf", serverTransportListener, tcpServerParams,
            null, serverConfigurator).get(10, TimeUnit.SECONDS);
        try {
            final var clientConfig = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withTcpParameters(tcpClientParams)
                .withSshParameters(new SshClientParametersBuilder()
                    .setClientIdentity(new ClientIdentityBuilder().setUsername(USERNAME).build()).build())
                .withSshConfigurator(clientConfigurator)
                .withSessionListener(sessionListener)
                .withConnectionTimeoutMillis(10_000)
                .build();
            assertNotNull(factory.createClient(clientConfig));
            verify(serverTransportListener, timeout(10_000L))
                .onTransportChannelEstablished(any(TransportChannel.class));
        } finally {
            server.shutdown().get(1, TimeUnit.SECONDS);
        }
    }
}
