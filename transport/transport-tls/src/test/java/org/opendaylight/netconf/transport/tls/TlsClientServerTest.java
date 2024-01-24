/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.transport.tls.KeyStoreUtils.buildKeyManagerFactory;
import static org.opendaylight.netconf.transport.tls.KeyStoreUtils.buildTrustManagerFactory;
import static org.opendaylight.netconf.transport.tls.KeyStoreUtils.newKeyStore;
import static org.opendaylight.netconf.transport.tls.KeyUtils.EC_ALGORITHM;
import static org.opendaylight.netconf.transport.tls.KeyUtils.RSA_ALGORITHM;
import static org.opendaylight.netconf.transport.tls.TestUtils.buildEndEntityCertWithKeyGrouping;
import static org.opendaylight.netconf.transport.tls.TestUtils.buildInlineOrTruststore;
import static org.opendaylight.netconf.transport.tls.TestUtils.generateX509CertData;
import static org.opendaylight.netconf.transport.tls.TestUtils.isRSA;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev231228.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev231228.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev231228.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev231228.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev231228.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228.tls.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228.tls.client.grouping.ServerAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228.tls.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228.tls.server.grouping.ServerIdentityBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
class TlsClientServerTest {

    @Mock
    private TcpClientGrouping tcpClientConfig;
    @Mock
    private TlsClientGrouping tlsClientConfig;
    @Mock
    private TransportChannelListener clientListener;
    @Mock
    private TcpServerGrouping tcpServerConfig;
    @Mock
    private TlsServerGrouping tlsServerConfig;
    @Mock
    private TransportChannelListener serverListener;
    @Mock
    private TransportChannelListener otherServerListener;

    @Captor
    ArgumentCaptor<TransportChannel> clientTransportChannelCaptor;
    @Captor
    ArgumentCaptor<TransportChannel> serverTransportChannelCaptor;

    private static EventLoopGroup group;
    private ServerSocket socket;

    @BeforeAll
    static void beforeAll() {
        group = NettyTransportSupport.newEventLoopGroup("IntegrationTest");
    }

    @AfterAll
    static void afterAll() {
        group.shutdownGracefully();
        group = null;
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

    @ParameterizedTest(name = "TLS using X.509 certificates: {0}")
    @ValueSource(strings = {RSA_ALGORITHM, EC_ALGORITHM})
    void itWithCertificateConfig(final String algorithm) throws Exception {

        final var data = generateX509CertData(algorithm);

        // common config parts
        var inlineOrKeystore = buildEndEntityCertWithKeyGrouping(
                SubjectPublicKeyInfoFormat.VALUE, data.publicKey(),
                isRSA(algorithm) ? RsaPrivateKeyFormat.VALUE : EcPrivateKeyFormat.VALUE,
                data.privateKey(), data.certBytes()).getInlineOrKeystore();
        var inlineOrTrustStore = buildInlineOrTruststore(Map.of("cert", data.certBytes()));

        // client config
        final var clientIdentity = new ClientIdentityBuilder()
            .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228
                .tls.client.grouping.client.identity.auth.type.CertificateBuilder()
                .setCertificate(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228
                    .tls.client.grouping.client.identity.auth.type.certificate.CertificateBuilder()
                    .setInlineOrKeystore(inlineOrKeystore)
                    .build())
                .build())
            .build();
        final var serverAuth = new ServerAuthenticationBuilder()
            .setCaCerts(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev231228
                .tls.client.grouping.server.authentication.CaCertsBuilder()
                .setInlineOrTruststore(inlineOrTrustStore)
                .build())
            .build();
        when(tlsClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(tlsClientConfig.getServerAuthentication()).thenReturn(serverAuth);

        // server config
        final var serverIdentity = new ServerIdentityBuilder()
            .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228
                .tls.server.grouping.server.identity.auth.type.CertificateBuilder()
                .setCertificate(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228
                    .tls.server.grouping.server.identity.auth.type.certificate.CertificateBuilder()
                    .setInlineOrKeystore(inlineOrKeystore)
                    .build())
                .build())
            .build();
        final var clientAuth = new ClientAuthenticationBuilder()
            .setCaCerts(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228
                .tls.server.grouping.client.authentication.CaCertsBuilder()
                .setInlineOrTruststore(inlineOrTrustStore)
                .build())
            .build();
        when(tlsServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(tlsServerConfig.getClientAuthentication()).thenReturn(clientAuth);

        integrationTest(
            () -> TLSServer.listen(serverListener, NettyTransportSupport.newServerBootstrap().group(group),
                tcpServerConfig, tlsServerConfig),
            () -> TLSClient.connect(clientListener, NettyTransportSupport.newBootstrap().group(group),
                tcpClientConfig, tlsClientConfig)
        );
    }

    @Test
    @DisplayName("External SslHandlerFactory integration")
    void sslHandlerFactory() throws Exception {

        final var serverKs = buildKeystoreWithGeneratedCert(RSA_ALGORITHM);
        final var clientKs = buildKeystoreWithGeneratedCert(EC_ALGORITHM);
        final var serverContext = SslContextBuilder.forServer(buildKeyManagerFactory(serverKs))
            .clientAuth(ClientAuth.REQUIRE).trustManager(buildTrustManagerFactory(clientKs)).build();
        final var clientContext = SslContextBuilder.forClient().keyManager(buildKeyManagerFactory(clientKs))
            .trustManager(buildTrustManagerFactory(serverKs)).build();

        integrationTest(
            () -> TLSServer.listen(serverListener, NettyTransportSupport.newServerBootstrap().group(group),
                tcpServerConfig, channel -> serverContext.newHandler(channel.alloc())),
            () -> TLSClient.connect(clientListener, NettyTransportSupport.newBootstrap().group(group),
                tcpClientConfig, channel -> clientContext.newHandler(channel.alloc()))
        );
    }

    private static KeyStore buildKeystoreWithGeneratedCert(final String algorithm) throws Exception {
        final var data = generateX509CertData(algorithm);
        final var ret = newKeyStore();
        ret.setCertificateEntry("certificate", data.certificate());
        ret.setKeyEntry("key", data.keyPair().getPrivate(), new char[0], new Certificate[]{data.certificate()});
        return ret;
    }

    private void integrationTest(final Builder<TLSServer> serverBuilder,
            final Builder<TLSClient> clientBuilder) throws Exception {
        // start server
        final var server = serverBuilder.build().get(2, TimeUnit.SECONDS);
        try {
            // connect with client
            final var client = clientBuilder.build().get(2, TimeUnit.SECONDS);
            try {
                verify(serverListener, timeout(500))
                        .onTransportChannelEstablished(serverTransportChannelCaptor.capture());
                verify(clientListener, timeout(500))
                        .onTransportChannelEstablished(clientTransportChannelCaptor.capture());
                // validate channels are in expected state
                var serverChannel = assertChannel(serverTransportChannelCaptor.getAllValues());
                var clientChannel = assertChannel(clientTransportChannelCaptor.getAllValues());
                // validate channels are connecting same sockets
                assertEquals(serverChannel.remoteAddress(), clientChannel.localAddress());
                assertEquals(serverChannel.localAddress(), clientChannel.remoteAddress());

            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Call-Home client + 2 servers with external SslHandlerFactory integration")
    void callHome() throws Exception {

        final var serverKs = buildKeystoreWithGeneratedCert(RSA_ALGORITHM);
        final var clientKs = buildKeystoreWithGeneratedCert(EC_ALGORITHM);
        final var serverContext = SslContextBuilder.forServer(buildKeyManagerFactory(serverKs))
            .clientAuth(ClientAuth.REQUIRE).trustManager(buildTrustManagerFactory(clientKs)).build();
        final var clientContext = SslContextBuilder.forClient().keyManager(buildKeyManagerFactory(clientKs))
            .trustManager(buildTrustManagerFactory(serverKs)).build();

        // start call-home client
        final var client = TLSClient.listen(clientListener, NettyTransportSupport.newServerBootstrap().group(group),
            tcpServerConfig, channel -> clientContext.newHandler(channel.alloc())).get(2, TimeUnit.SECONDS);
        try {
            // connect with call-home servers
            final var server1 = TLSServer.connect(serverListener, NettyTransportSupport.newBootstrap().group(group),
                tcpClientConfig, channel -> serverContext.newHandler(channel.alloc())).get(2, TimeUnit.SECONDS);
            final var server2 = TLSServer.connect(otherServerListener,
                NettyTransportSupport.newBootstrap().group(group),
                tcpClientConfig, channel -> serverContext.newHandler(channel.alloc())).get(2, TimeUnit.SECONDS);
            try {
                verify(serverListener, timeout(500))
                    .onTransportChannelEstablished(serverTransportChannelCaptor.capture());
                verify(otherServerListener, timeout(500))
                    .onTransportChannelEstablished(serverTransportChannelCaptor.capture());
                verify(clientListener, timeout(500).times(2))
                    .onTransportChannelEstablished(clientTransportChannelCaptor.capture());
                // extract channels sorted by server address
                var serverChannels = assertChannels(serverTransportChannelCaptor.getAllValues(), 2,
                    Comparator.comparing((Channel channel) -> channel.localAddress().toString()));
                var clientChannels = assertChannels(clientTransportChannelCaptor.getAllValues(), 2,
                    Comparator.comparing((Channel channel) -> channel.remoteAddress().toString()));
                for (int i = 0; i < 2; i++) {
                    // validate channels are connecting same sockets
                    assertEquals(serverChannels.get(i).remoteAddress(), clientChannels.get(i).localAddress());
                    assertEquals(serverChannels.get(i).localAddress(), clientChannels.get(i).remoteAddress());
                }

            } finally {
                server1.shutdown().get(2, TimeUnit.SECONDS);
                server2.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            client.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private static Channel assertChannel(final List<TransportChannel> transportChannels) {
        assertNotNull(transportChannels);
        assertEquals(1, transportChannels.size());
        final var channel = assertInstanceOf(TLSTransportChannel.class, transportChannels.get(0)).channel();
        assertNotNull(channel);
        assertTrue(channel.isOpen()); // connection is open
        assertNotNull(channel.pipeline().get(SslHandler.class)); //  has an SSL handler within a pipeline
        return channel;
    }

    private static List<Channel> assertChannels(final List<TransportChannel> transportChannels, final int channelsNum,
        final Comparator<Channel> comparator) {
        assertNotNull(transportChannels);
        assertEquals(channelsNum, transportChannels.size());
        final var res = new ArrayList<Channel>(channelsNum);
        for (var transportChannel : transportChannels) {
            final var channel = assertInstanceOf(TLSTransportChannel.class, transportChannel).channel();
            assertNotNull(channel);
            assertTrue(channel.isOpen());
            assertNotNull(channel.pipeline().get(SslHandler.class));
            res.add(channel);
        }
        res.sort(comparator);
        return res;
    }

    private interface Builder<T extends TLSTransportStack> {
        ListenableFuture<T> build() throws UnsupportedConfigurationException;
    }
}
