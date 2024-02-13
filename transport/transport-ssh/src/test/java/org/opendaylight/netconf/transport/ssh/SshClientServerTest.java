/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthHostBased;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthWithPublicKey;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityHostBased;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityWithPublicKey;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerAuthWithCertificate;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerAuthWithPublicKey;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerIdentityWithCertificate;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerIdentityWithKeyPair;
import static org.opendaylight.netconf.transport.ssh.TestUtils.generateKeyPairWithCertificate;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.Crypt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.PasswordIdentityProvider;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.opendaylight.netconf.shaded.sshd.server.session.ServerSession;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev240208.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev240208.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev240208.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev240208.ssh.client.grouping.ServerAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev240208.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev240208.ssh.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev240208.ssh.server.grouping.ServerIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev240208.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev240208.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
public class SshClientServerTest {
    private static final String RSA = "RSA";
    private static final String EC = "EC";
    private static final String USER = "user";
    private static final String PASSWORD = "pa$$w0rd";
    private static final String SUBSYSTEM = "subsystem";
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final AtomicReference<String> USERNAME = new AtomicReference<>(USER);

    private static SSHTransportStackFactory FACTORY;

    @Mock
    private TcpClientGrouping tcpClientConfig;
    @Mock
    private SshClientGrouping sshClientConfig;
    @Mock
    private TransportChannelListener clientListener;
    @Mock
    private TcpServerGrouping tcpServerConfig;
    @Mock
    private SshServerGrouping sshServerConfig;
    @Mock
    private TransportChannelListener serverListener;

    @Captor
    ArgumentCaptor<TransportChannel> clientTransportChannelCaptor;
    @Captor
    ArgumentCaptor<TransportChannel> serverTransportChannelCaptor;

    private ServerSocket socket;

    @BeforeAll
    static void beforeAll() {
        FACTORY = new SSHTransportStackFactory("IntegrationTest", 0);
    }

    @AfterAll
    static void afterAll() {
        FACTORY.close();
    }

    @BeforeEach
    void beforeEach() throws IOException {

        // create temp socket to get available port for test
        socket = new ServerSocket(0);
        final var localAddress = IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress());
        final var localPort = new PortNumber(Uint16.valueOf(socket.getLocalPort()));
        socket.close();

        when(tcpServerConfig.getLocalAddress()).thenReturn(localAddress);
        when(tcpServerConfig.requireLocalAddress()).thenCallRealMethod();
        when(tcpServerConfig.getLocalPort()).thenReturn(localPort);
        when(tcpServerConfig.requireLocalPort()).thenCallRealMethod();

        when(tcpClientConfig.getRemoteAddress()).thenReturn(new Host(localAddress));
        when(tcpClientConfig.requireRemoteAddress()).thenCallRealMethod();
        when(tcpClientConfig.getRemotePort()).thenReturn(localPort);
        when(tcpClientConfig.requireRemotePort()).thenCallRealMethod();
    }

    @ParameterizedTest(name = "SSH Server Host Key Verification -- {0}")
    @MethodSource("itServerKeyVerifyArgs")
    void itServerKeyVerify(final String testDesc, final ServerIdentity serverIdentity,
            final ServerAuthentication serverAuth) throws Exception {
        final var clientIdentity = buildClientIdentityWithPassword(getUsername(), PASSWORD);
        final var clientAuth = buildClientAuthWithPassword(getUsernameAndUpdate(), "$0$" + PASSWORD);
        when(sshClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(sshClientConfig.getServerAuthentication()).thenReturn(serverAuth);
        when(sshServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(sshServerConfig.getClientAuthentication()).thenReturn(clientAuth);
        integrationTest(
            () -> FACTORY.listenServer(SUBSYSTEM, serverListener, tcpServerConfig, sshServerConfig),
            () -> FACTORY.connectClient(SUBSYSTEM, clientListener, tcpClientConfig, sshClientConfig));
    }

    private static Stream<Arguments> itServerKeyVerifyArgs() throws Exception {
        final var rsaKeyData = generateKeyPairWithCertificate(RSA);
        final var ecKeyData = generateKeyPairWithCertificate(EC);
        return Stream.of(
                Arguments.of("RSA public key",
                        buildServerIdentityWithKeyPair(rsaKeyData), buildServerAuthWithPublicKey(rsaKeyData)),
                Arguments.of("EC public key",
                        buildServerIdentityWithKeyPair(ecKeyData), buildServerAuthWithPublicKey(ecKeyData)),
                Arguments.of("RSA certificate",
                        buildServerIdentityWithCertificate(rsaKeyData), buildServerAuthWithCertificate(rsaKeyData)),
                Arguments.of("EC certificate",
                        buildServerIdentityWithCertificate(ecKeyData), buildServerAuthWithCertificate(ecKeyData))
        );
    }

    @ParameterizedTest(name = "SSH User Auth using {0}")
    @MethodSource("itUserAuthArgs")
    void itUserAuth(final String testDesc, final ClientIdentity clientIdentity, final ClientAuthentication clientAuth)
            throws Exception {
        final var serverIdentity = buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA)); // required
        when(sshClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(sshClientConfig.getServerAuthentication()).thenReturn(null); // Accept all keys
        when(sshServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(sshServerConfig.getClientAuthentication()).thenReturn(clientAuth);
        integrationTest(
            () -> FACTORY.listenServer(SUBSYSTEM, serverListener, tcpServerConfig, sshServerConfig),
            () -> FACTORY.connectClient(SUBSYSTEM, clientListener, tcpClientConfig, sshClientConfig));
    }

    private static Stream<Arguments> itUserAuthArgs() throws Exception {
        final var rsaKeyData = generateKeyPairWithCertificate(RSA);
        final var ecKeyData = generateKeyPairWithCertificate(EC);
        return Stream.of(
                Arguments.of("Password -- clear text ",
                        buildClientIdentityWithPassword(getUsername(), PASSWORD),
                        buildClientAuthWithPassword(getUsernameAndUpdate(), "$0$" + PASSWORD)),
                Arguments.of("Password -- MD5",
                        buildClientIdentityWithPassword(getUsername(), PASSWORD),
                        buildClientAuthWithPassword(getUsernameAndUpdate(), Crypt.crypt(PASSWORD, "$1$md5salt"))),
                Arguments.of("Password -- SHA-256",
                        buildClientIdentityWithPassword(getUsername(), PASSWORD),
                        buildClientAuthWithPassword(getUsernameAndUpdate(),
                                Crypt.crypt(PASSWORD, "$5$sha256salt"))),
                Arguments.of("Password -- SHA-512 with rounds",
                        buildClientIdentityWithPassword(getUsername(), PASSWORD),
                        buildClientAuthWithPassword(getUsernameAndUpdate(),
                                Crypt.crypt(PASSWORD, "$6$rounds=4500$sha512salt"))),
                Arguments.of("HostBased -- RSA keys",
                        buildClientIdentityHostBased(getUsername(), rsaKeyData),
                        buildClientAuthHostBased(getUsernameAndUpdate(), rsaKeyData)),
                Arguments.of("HostBased -- EC keys",
                        buildClientIdentityHostBased(getUsername(), ecKeyData),
                        buildClientAuthHostBased(getUsernameAndUpdate(), ecKeyData)),
                Arguments.of("PublicKey -- RSA keys",
                        buildClientIdentityWithPublicKey(getUsername(), rsaKeyData),
                        buildClientAuthWithPublicKey(getUsernameAndUpdate(), rsaKeyData)),
                Arguments.of("PublicKey -- EC keys",
                        buildClientIdentityWithPublicKey(getUsername(), ecKeyData),
                        buildClientAuthWithPublicKey(getUsernameAndUpdate(), ecKeyData))
        );
    }

    private static String getUsername() {
        return USERNAME.get();
    }

    /**
     * Update username for next test.
     */
    private static String getUsernameAndUpdate() {
        return USERNAME.getAndSet(USER + COUNTER.incrementAndGet());
    }

    private void integrationTest(final Builder<SSHServer> serverBuilder,
            final Builder<SSHClient> clientBuilder) throws Exception {
        // start server
        final var server = serverBuilder.build().get(2, TimeUnit.SECONDS);
        try {
            // connect with client
            final var client = clientBuilder.build().get(2, TimeUnit.SECONDS);
            try {
                verify(serverListener, timeout(10_000))
                        .onTransportChannelEstablished(serverTransportChannelCaptor.capture());
                verify(clientListener, timeout(10_000))
                        .onTransportChannelEstablished(clientTransportChannelCaptor.capture());
                // validate channels are in expected state
                var serverChannel = assertChannel(serverTransportChannelCaptor.getAllValues());
                var clientChannel = assertChannel(clientTransportChannelCaptor.getAllValues());
                // validate channels are connecting same sockets
                assertEquals(serverChannel.remoteAddress(), clientChannel.localAddress());
                assertEquals(serverChannel.localAddress(), clientChannel.remoteAddress());
                // validate sessions are authenticated
                assertSession(ServerSession.class, server.getSessions());
                assertSession(ClientSession.class, client.getSessions());

            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("External service integration")
    void externalServiceIntegration() throws Exception {
        final var username = getUsernameAndUpdate();
        when(sshClientConfig.getClientIdentity()).thenReturn(usernameOnlyIdentity(username));
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);
        integrationTest(
            () -> FACTORY.listenServer(SUBSYSTEM, serverListener, tcpServerConfig, null, serverConfigurator(username)),
            () -> FACTORY.connectClient(SUBSYSTEM, clientListener, tcpClientConfig, sshClientConfig,
                clientConfigurator(username)));
    }

    @Test
    @DisplayName("Call-home protocol support with services integration")
    void callHome() throws Exception {
        final var username = getUsernameAndUpdate();
        when(sshClientConfig.getClientIdentity()).thenReturn(usernameOnlyIdentity(username));
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);

        // start call-home client first, accepting inbound tcp connections
        final var client = FACTORY.listenClient(SUBSYSTEM, clientListener, tcpServerConfig, sshClientConfig,
                clientConfigurator(username)).get(2, TimeUnit.SECONDS);
        try {
            // start a call-home server, init connection
            final var server = FACTORY.connectServer(SUBSYSTEM, serverListener, tcpClientConfig, null,
                    serverConfigurator(username)).get(2, TimeUnit.SECONDS);
            try {
                verify(serverListener, timeout(10_000))
                    .onTransportChannelEstablished(serverTransportChannelCaptor.capture());
                verify(clientListener, timeout(10_000))
                    .onTransportChannelEstablished(clientTransportChannelCaptor.capture());
                // validate channels are in expected state
                var serverChannel = assertChannel(serverTransportChannelCaptor.getAllValues());
                var clientChannel = assertChannel(clientTransportChannelCaptor.getAllValues());
                // validate channels are connecting same sockets
                assertEquals(serverChannel.remoteAddress(), clientChannel.localAddress());
                assertEquals(serverChannel.localAddress(), clientChannel.remoteAddress());
                // validate sessions are authenticated
                assertSession(ClientSession.class, client.getSessions());
                assertSession(ServerSession.class, server.getSessions());

            } finally {
                server.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            client.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private static Channel assertChannel(final List<TransportChannel> transportChannels) {
        assertNotNull(transportChannels);
        assertEquals(1, transportChannels.size());
        final var channel = assertInstanceOf(SSHTransportChannel.class, transportChannels.get(0)).channel();
        assertNotNull(channel);
        assertTrue(channel.isOpen());
        return channel;
    }

    private static <T extends Session> void assertSession(final Class<T> type, final Collection<Session> sessions) {
        assertNotNull(sessions);
        assertEquals(1, sessions.size());
        final T session = assertInstanceOf(type, sessions.iterator().next());
        assertTrue(session.isAuthenticated());
    }

    private static ClientIdentity usernameOnlyIdentity(final String username) {
        return new ClientIdentityBuilder().setUsername(username).build();
    }

    private static ServerFactoryManagerConfigurator serverConfigurator(final String username) {
        return factoryManager -> {
            // authenticate user by credentials and generate host key
            factoryManager.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
            factoryManager.setPasswordAuthenticator(
                (usr, psw, session) -> username.equals(usr) && PASSWORD.equals(psw));
            factoryManager.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        };
    }

    private static ClientFactoryManagerConfigurator clientConfigurator(final String username) {
        return factoryManager -> {
            factoryManager.setPasswordIdentityProvider(PasswordIdentityProvider.wrapPasswords(PASSWORD));
            factoryManager.setUserAuthFactories(List.of(
                new org.opendaylight.netconf.shaded.sshd.client.auth.password.UserAuthPasswordFactory()));
        };
    }

    @Test
    @DisplayName("Handle channel inactive event")
    void handleChannelInactive() throws Exception {
        final var username = getUsernameAndUpdate();
        when(sshClientConfig.getClientIdentity()).thenReturn(usernameOnlyIdentity(username));
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);

        // place channelInactive handlers on a server side channel when connection is established
        final var firstHandlerFuture = SettableFuture.<Boolean>create();
        final var lastHandlerFuture = SettableFuture.<Boolean>create();
        final var serverTransportListener = new TransportChannelListener() {
            @Override
            public void onTransportChannelEstablished(final TransportChannel channel) {
                channel.channel().pipeline().addFirst("FIRST", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                        firstHandlerFuture.set(Boolean.TRUE);
                        ctx.fireChannelInactive();
                    }
                });
                channel.channel().pipeline().addLast("LAST", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                        lastHandlerFuture.set(Boolean.TRUE);
                        ctx.fireChannelInactive();
                    }
                });
            }

            @Override
            public void onTransportChannelFailed(final Throwable cause) {
                // not used
            }
        };

        final var server = FACTORY.listenServer(SUBSYSTEM, serverTransportListener, tcpServerConfig, null,
                serverConfigurator(username)).get(2, TimeUnit.SECONDS);
        try {
            // connect with client
            final var client = FACTORY.connectClient(SUBSYSTEM, clientListener, tcpClientConfig, sshClientConfig,
                clientConfigurator(username)).get(2, TimeUnit.SECONDS);
            try {
                verify(clientListener, timeout(10_000)).onTransportChannelEstablished(any(TransportChannel.class));
            } finally {
                // disconnect client
                client.shutdown().get(2, TimeUnit.SECONDS);
                // validate channel closure on server side is handled properly:
                // both first and last handlers expected to be triggered
                // indicating there is no obstacles for the event in a channel pipeline
                assertEquals(Boolean.TRUE, firstHandlerFuture.get(1, TimeUnit.SECONDS));
                assertEquals(Boolean.TRUE, lastHandlerFuture.get(1, TimeUnit.SECONDS));
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @FunctionalInterface
    private interface Builder<T extends SSHTransportStack> {
        ListenableFuture<T> build() throws UnsupportedConfigurationException;
    }
}
