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
import static org.opendaylight.netconf.transport.tls.KeyUtils.EC_ALGORITHM;
import static org.opendaylight.netconf.transport.tls.KeyUtils.RSA_ALGORITHM;
import static org.opendaylight.netconf.transport.tls.TestUtils.buildEndEntityCertWithKeyGrouping;
import static org.opendaylight.netconf.transport.tls.TestUtils.buildInlineOrTruststore;
import static org.opendaylight.netconf.transport.tls.TestUtils.generateX509CertData;
import static org.opendaylight.netconf.transport.tls.TestUtils.isRSA;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.tls.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.tls.client.grouping.ServerAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417.tls.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417.tls.server.grouping.ServerIdentityBuilder;
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
            .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417
                .tls.client.grouping.client.identity.auth.type.CertificateBuilder()
                .setCertificate(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417
                    .tls.client.grouping.client.identity.auth.type.certificate.CertificateBuilder()
                    .setInlineOrKeystore(inlineOrKeystore)
                    .build())
                .build())
            .build();
        final var serverAuth = new ServerAuthenticationBuilder()
            .setCaCerts(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417
                .tls.client.grouping.server.authentication.CaCertsBuilder()
                .setInlineOrTruststore(inlineOrTrustStore)
                .build())
            .build();
        when(tlsClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(tlsClientConfig.getServerAuthentication()).thenReturn(serverAuth);

        // server config
        final var serverIdentity = new ServerIdentityBuilder()
            .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417
                .tls.server.grouping.server.identity.auth.type.CertificateBuilder()
                .setCertificate(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417
                    .tls.server.grouping.server.identity.auth.type.certificate.CertificateBuilder()
                    .setInlineOrKeystore(inlineOrKeystore)
                    .build())
                .build())
            .build();
        final var clientAuth = new ClientAuthenticationBuilder()
            .setCaCerts(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev230417
                .tls.server.grouping.client.authentication.CaCertsBuilder()
                .setInlineOrTruststore(inlineOrTrustStore)
                .build())
            .build();
        when(tlsServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(tlsServerConfig.getClientAuthentication()).thenReturn(clientAuth);

        integrationTest();
    }

    private void integrationTest() throws Exception {
        // start server
        final var server = TLSServer.listen(serverListener, NettyTransportSupport.newServerBootstrap().group(group),
                tcpServerConfig, tlsServerConfig).get(2, TimeUnit.SECONDS);
        try {
            // connect with client
            final var client = TLSClient.connect(clientListener, NettyTransportSupport.newBootstrap().group(group),
                    tcpClientConfig, tlsClientConfig).get(2, TimeUnit.SECONDS);
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

    private static Channel assertChannel(final List<TransportChannel> transportChannels) {
        assertNotNull(transportChannels);
        assertEquals(1, transportChannels.size());
        final var channel = assertInstanceOf(TLSTransportChannel.class, transportChannels.get(0)).channel();
        assertNotNull(channel);
        assertTrue(channel.isOpen()); // connection is open
        assertNotNull(channel.pipeline().get(SslHandler.class)); //  has an SSL handler within a pipeline
        return channel;
    }
}
