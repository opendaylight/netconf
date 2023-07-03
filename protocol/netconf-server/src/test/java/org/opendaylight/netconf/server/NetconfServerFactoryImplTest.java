/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.server.api.NetconfServerFactory;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.ssh.SSHClient;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev221212.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.local.or.keystore.asymmetric.key.grouping.local.or.keystore.LocalBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev221212.local.or.keystore.asymmetric.key.grouping.local.or.keystore.local.LocalDefinitionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev230417.netconf.server.listen.stack.grouping.transport.ssh.ssh.SshServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev230417.netconf.server.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.ServerIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.ServerIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.client.authentication.UsersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.client.authentication.users.UserBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.server.identity.HostKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.server.identity.host.key.host.key.type.PublicKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev221212.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev221212.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
class NetconfServerFactoryImplTest {

    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0rd";
    private static final String RSA = "RSA";

    private static EventLoopGroup parentGroup;
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup clientGroup;
    @Mock
    private ServerChannelInitializer serverChannelInitializer;
    @Mock
    private TransportChannelListener clientListener;

    private NetconfServerFactory factory;
    private TcpServerGrouping tcpServerParams;
    private TcpClientGrouping tcpClientParams;

    @BeforeAll
    static void beforeAll() {
        parentGroup = NettyTransportSupport.newEventLoopGroup("parent");
        workerGroup = NettyTransportSupport.newEventLoopGroup("worker");
        clientGroup = NettyTransportSupport.newEventLoopGroup("client");
    }

    @AfterAll
    static void afterAll() {
        parentGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        clientGroup.shutdownGracefully();
    }

    @BeforeEach
    void beforeEach() throws Exception {
        factory = new NetconfServerFactoryImpl(serverChannelInitializer, parentGroup, workerGroup);

        // create temp socket to get available port for test
        final var socket = new ServerSocket(0);
        final var address = IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress());
        final var port = new PortNumber(Uint16.valueOf(socket.getLocalPort()));
        socket.close();

        tcpServerParams = new TcpServerParametersBuilder().setLocalAddress(address).setLocalPort(port).build();
        tcpClientParams =
            new TcpClientParametersBuilder().setRemoteAddress(new Host(address)).setRemotePort(port).build();
    }

    @Test
    void tcpServer() throws Exception {
        final var server = factory.createTcpServer(tcpServerParams).get(1, TimeUnit.SECONDS);
        try {
            final var client = TCPClient.connect(clientListener,
                NettyTransportSupport.newBootstrap().group(clientGroup), tcpClientParams).get(1, TimeUnit.SECONDS);
            try {
                verify(serverChannelInitializer, timeout(1000L)).initialize(any(Channel.class), any());
                verify(clientListener, timeout(1000L)).onTransportChannelEstablished(any(TransportChannel.class));
            } finally {
                client.shutdown().get(1, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void sshServer() throws Exception {
        final var user = new UserBuilder().setName(USERNAME).setPassword(new CryptHash("$0$" + PASSWORD)).build();
        final var sshServerConfig = new SshServerParametersBuilder()
            .setServerIdentity(buildSshServerIdentityWithKeyPair())
            .setClientAuthentication(
                new ClientAuthenticationBuilder().setUsers(
                    new UsersBuilder().setUser(Map.of(user.key(), user)).build()
                ).build()
            ).build();

        assertSshServer(factory.createSshServer(tcpServerParams, sshServerConfig), sshClientParams());
    }

    @Test
    void sshServerExtInitializer() throws Exception {
        final Consumer<ServerFactoryManager> initializer = factoryMgr -> {
            factoryMgr.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
            factoryMgr.setPasswordAuthenticator(((username, password, session) -> true));
            factoryMgr.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        };
        assertSshServer(factory.createSshServer(tcpServerParams, null, initializer), sshClientParams());
    }

    void assertSshServer(final ListenableFuture<SSHServer> serverFuture, final SshClientParameters sshClientParams)
        throws Exception {
        final var server = serverFuture.get(2, TimeUnit.SECONDS);
        try {
            final var client = SSHClient.connect(clientListener,
                    NettyTransportSupport.newBootstrap().group(clientGroup), tcpClientParams, sshClientParams)
                .get(2, TimeUnit.SECONDS);
            try {
                // FIXME commented line requires netconf client to trigger netconf subsystem initialization on server
                // verify(serverChannelInitializer, timeout(10_000L)).initialize(any(Channel.class), any());
                verify(clientListener, timeout(10_000L)).onTransportChannelEstablished(any(TransportChannel.class));
            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private static ServerIdentity buildSshServerIdentityWithKeyPair() throws Exception {
        final var keyPair = KeyPairGenerator.getInstance(RSA).generateKeyPair();
        final var localDef = new LocalDefinitionBuilder()
            .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
            .setPublicKey(keyPair.getPublic().getEncoded())
            .setPrivateKeyFormat(RsaPrivateKeyFormat.VALUE)
            .setPrivateKeyType(
                new CleartextPrivateKeyBuilder().setCleartextPrivateKey(
                    keyPair.getPrivate().getEncoded()
                ).build()
            ).build();
        final var local = new LocalBuilder().setLocalDefinition(localDef).build();
        var publicKey = new PublicKeyBuilder().setPublicKey(
            new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212
                .ssh.server.grouping.server.identity.host.key.host.key.type._public.key
                .PublicKeyBuilder().setLocalOrKeystore(local).build()
        ).build();
        return new ServerIdentityBuilder().setHostKey(
            List.of(new HostKeyBuilder().setName("test-name").setHostKeyType(publicKey).build())
        ).build();
    }

    private static SshClientParameters sshClientParams() {
        return new SshClientParametersBuilder().setClientIdentity(
            new ClientIdentityBuilder().setUsername(USERNAME).setPassword(
                new PasswordBuilder().setPasswordType(
                    new CleartextPasswordBuilder().setCleartextPassword(PASSWORD).build()
                ).build()
            ).build()
        ).build();
    }
}
