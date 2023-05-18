/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerIdentityWithKeyPair;
import static org.opendaylight.netconf.transport.ssh.TestUtils.generateKeyPairWithCertificate;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev221212.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev221212.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.client.transport.config.rev230512.client.transport.config.grouping.ClientSshConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.client.transport.config.rev230512.client.transport.config.grouping.ClientSshConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.client.transport.config.rev230512.client.transport.config.grouping.ClientTcpConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.server.transport.config.rev230512.server.transport.config.grouping.ServerSshConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.server.transport.config.rev230512.server.transport.config.grouping.ServerTcpConfigBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
public class NetconfServerFactoryImplTest {

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
    private TcpServerGrouping tcpServerConfig;
    private TcpClientGrouping tcpClientConfig;

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
        final var address = IetfInetUtil.INSTANCE.ipAddressFor(InetAddress.getLoopbackAddress());
        final var port = new PortNumber(Uint16.valueOf(socket.getLocalPort()));
        socket.close();

        tcpServerConfig = new ServerTcpConfigBuilder().setLocalAddress(address).setLocalPort(port).build();
        tcpClientConfig = new ClientTcpConfigBuilder().setRemoteAddress(new Host(address)).setRemotePort(port).build();
    }

    @Test
    void tcpServer() throws Exception {
        final var server = factory.createTcpServer(tcpServerConfig).get(1, TimeUnit.SECONDS);
        try {
            final var client = TCPClient.connect(clientListener,
                NettyTransportSupport.newBootstrap().group(clientGroup), tcpClientConfig).get(1, TimeUnit.SECONDS);
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
        final var sshServerConfig = new ServerSshConfigBuilder()
            .setServerIdentity(
                buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA)))
            .setClientAuthentication(
                buildClientAuthWithPassword(USERNAME, "$0$" + PASSWORD))
            .build();
        final var sshClientConfig = new ClientSshConfigBuilder()
            .setClientIdentity(buildClientIdentityWithPassword(USERNAME, PASSWORD)).build();

        assertSshServer(factory.createSshServer(tcpServerConfig, sshServerConfig), sshClientConfig);
    }

    @Test
    void sshServerExtInitializer() throws Exception {
        final Consumer<ServerFactoryManager> initializer = factoryMgr -> {
            factoryMgr.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
            factoryMgr.setPasswordAuthenticator(((username, password, session) -> true));
            factoryMgr.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        };
        final var sshClientConfig = new ClientSshConfigBuilder()
            .setClientIdentity(buildClientIdentityWithPassword(USERNAME, PASSWORD)).build();

        assertSshServer(factory.createSshServer(tcpServerConfig, null, initializer), sshClientConfig);
    }

    void assertSshServer(final ListenableFuture<SSHServer> serverFuture, final ClientSshConfig sshClientConfig)
            throws Exception {
        final var server = serverFuture.get(1, TimeUnit.SECONDS);
        try {
            final var client = SSHClient.connect(clientListener,
                    NettyTransportSupport.newBootstrap().group(clientGroup), tcpClientConfig, sshClientConfig)
                .get(1, TimeUnit.SECONDS);
            try {
                // FIXME commented line requires netconf client to trigger netconf subsystem initialization on server
                // verify(serverChannelInitializer, timeout(1000L)).initialize(any(Channel.class), any());
                verify(clientListener, timeout(1000L)).onTransportChannelEstablished(any(TransportChannel.class));
            } finally {
                client.shutdown().get(1, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(1, TimeUnit.SECONDS);
        }
    }
}
