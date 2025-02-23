/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.Promise;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.netconf.server.NetconfServerSession;
import org.opendaylight.netconf.server.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.netconf.server.impl.DefaultSessionIdProvider;
import org.opendaylight.netconf.server.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.netconf.transport.tls.FixedSslHandlerFactory;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class CallHomeTlsServerTest {
    private static final Logger LOG = LoggerFactory.getLogger("TEST");

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final char[] EMPTY_SECRET = new char[0];
    private static final long TIMEOUT = 5000L;
    private static final Capabilities EMPTY_CAPABILITIES = new CapabilitiesBuilder().setCapability(Set.of()).build();

    @Mock
    private NetconfMonitoringService monitoringService;
    @Mock
    private SessionListener serverSessionListener;
    @Mock
    private NetconfClientSessionListener clientSessionListener;
    @Mock
    private CallHomeStatusRecorder statusRecorder;

    @Test
    void integrationTest() throws Exception {
        // certificates
        final var serverCert = generateCertData();
        final var clientCert1 = generateCertData();
        final var clientCert2 = generateCertData();
        final var clientCert3 = generateCertData();

        // SSL context for call-home server (acting as client): denies client 1, allows client 2,3
        final var serverCtx = SslContextBuilder.forClient()
            .keyManager(keyManagerFor(serverCert))
            .trustManager(trustManagerFor(clientCert2, clientCert3)).build();

        // SSL context for call-home clients (acting as servers)
        final var serverTrustMgr = trustManagerFor(serverCert);
        final var clientCtx1 = SslContextBuilder.forServer(keyManagerFor(clientCert1))
            .trustManager(serverTrustMgr).clientAuth(ClientAuth.REQUIRE).build();
        final var clientCtx2 = SslContextBuilder.forServer(keyManagerFor(clientCert2))
            .trustManager(serverTrustMgr).clientAuth(ClientAuth.REQUIRE).build();
        final var clientCtx3 = SslContextBuilder.forServer(keyManagerFor(clientCert3))
            .trustManager(serverTrustMgr).clientAuth(ClientAuth.REQUIRE).build();

        // Auth provider
        final var authProvider = new CallHomeTlsAuthProvider() {
            @Override
            public String idFor(final PublicKey publicKey) {
                // identify client 3 only
                return clientCert3.keyPair.getPublic().equals(publicKey) ? "client-id" : null;
            }

            @Override
            protected SslContext getSslContext(final SocketAddress remoteAddress) {
                return serverCtx;
            }
        };

        // Netconf layer for clients
        doReturn(serverSessionListener).when(monitoringService).getSessionListener();
        doReturn(EMPTY_CAPABILITIES).when(monitoringService).getCapabilities();

        final var timer = new DefaultNetconfTimer();

        final var negotiatorFactory = NetconfServerSessionNegotiatorFactory.builder()
            .setTimer(timer)
            .setAggregatedOpService(new AggregatedNetconfOperationServiceFactory())
            .setIdProvider(new DefaultSessionIdProvider())
            .setConnectionTimeoutMillis(TIMEOUT)
            .setMonitoringService(monitoringService)
            .build();
        final var netconfTransportListener = new TestNetconfServerInitializer(negotiatorFactory);

        // tcp layer for clients
        final var bootstrapFactory = new BootstrapFactory("call-home-test-client", 0);
        final var serverPort = serverPort();
        final var tcpConnectParams = new TcpClientParametersBuilder()
            .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress())))
            .setRemotePort(new PortNumber(Uint16.valueOf(serverPort))).build();

        // Session context manager
        final var contextMgr = new CallHomeTlsSessionContextManager(authProvider, statusRecorder) {
            // inject netconf session listener
            @Override
            public CallHomeTlsSessionContext createContext(final String id, final Channel channel) {
                return new CallHomeTlsSessionContext(id, channel, clientSessionListener, SettableFuture.create());
            }
        };

        // start Call-Home server
        final var server = CallHomeTlsServer.builder()
            .withAuthProvider(authProvider)
            .withSessionContextManager(contextMgr)
            .withStatusRecorder(statusRecorder)
            .withNegotiationFactory(new NetconfClientSessionNegotiatorFactory(timer, Optional.empty(), TIMEOUT,
                NetconfClientSessionNegotiatorFactory.DEFAULT_CLIENT_CAPABILITIES))
            .withPort(serverPort).build();

        TLSServer client1 = null;
        TLSServer client2 = null;
        TLSServer client3 = null;

        try {
            // client 1 rejected on handshake, ensure exception
            client1 = TLSServer.connect(
                netconfTransportListener, bootstrapFactory.newBootstrap(), tcpConnectParams,
                new FixedSslHandlerFactory(clientCtx1)).get(TIMEOUT, TimeUnit.MILLISECONDS);
            verify(statusRecorder, timeout(TIMEOUT).times(1))
                .onTransportChannelFailure(any(SSLHandshakeException.class));

            // client 2 rejected because it's not identified by public key accepted on handshake stage
            client2 = TLSServer.connect(
                netconfTransportListener, bootstrapFactory.newBootstrap(), tcpConnectParams,
                new FixedSslHandlerFactory(clientCtx2)).get(TIMEOUT, TimeUnit.MILLISECONDS);
            verify(statusRecorder, timeout(TIMEOUT).times(1))
                .reportUnknown(any(InetSocketAddress.class), eq(clientCert2.keyPair.getPublic()));

            // client 3 accepted
            client3 = TLSServer.connect(
                netconfTransportListener, bootstrapFactory.newBootstrap(), tcpConnectParams,
                new FixedSslHandlerFactory(clientCtx3)).get(TIMEOUT, TimeUnit.MILLISECONDS);
            // verify netconf session established
            verify(clientSessionListener, timeout(TIMEOUT).times(1)).onSessionUp(any(NetconfClientSession.class));
            verify(serverSessionListener, timeout(TIMEOUT).times(1)).onSessionUp(any(NetconfServerSession.class));
            verify(statusRecorder, times(1)).reportSuccess("client-id");

        } finally {
            server.close();
            shutdownClient(client1);
            shutdownClient(client2);
            shutdownClient(client3);
            timer.close();
        }

        // validate disconnect reported
        verify(serverSessionListener, timeout(TIMEOUT).times(1)).onSessionDown(any(NetconfServerSession.class));
        verify(clientSessionListener, timeout(TIMEOUT).times(1))
            .onSessionDown(any(NetconfClientSession.class), nullable(Exception.class));
        verify(statusRecorder, times(1)).reportDisconnected("client-id");
    }

    private static void shutdownClient(final @Nullable TLSServer client) throws Exception {
        if (client != null) {
            client.shutdown().get(TIMEOUT, TimeUnit.MILLISECONDS);
        }
    }

    public static int serverPort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static CertData generateCertData() throws Exception {
        // key pair
        final var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), new SecureRandom());
        final var keyPair = keyPairGenerator.generateKeyPair();
        // certificate
        final var now = Instant.now();
        final var contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        final var x500Name = new X500Name("CN=TestCertificate" + COUNTER.incrementAndGet());
        final var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now), Date.from(now.plus(Duration.ofDays(365))),
            x500Name,
            keyPair.getPublic());
        final var certificate = new JcaX509CertificateConverter()
            .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
        return new CertData(keyPair, certificate);
    }

    private static KeyManagerFactory keyManagerFor(final CertData... certs) throws Exception {
        final var keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManager.init(keyStoreWithCerts(certs), EMPTY_SECRET);
        return keyManager;
    }

    private static TrustManagerFactory trustManagerFor(final CertData... certs) throws Exception {
        final var trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManager.init(keyStoreWithCerts(certs));
        return trustManager;
    }

    private static KeyStore keyStoreWithCerts(final CertData... certs) throws Exception {
        final var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        for (var certData : certs) {
            keyStore.setCertificateEntry("cert" + COUNTER.incrementAndGet(), certData.certificate());
            keyStore.setKeyEntry("key" + COUNTER.incrementAndGet(), certData.keyPair().getPrivate(),
                EMPTY_SECRET, new Certificate[]{certData.certificate()});
        }
        return keyStore;
    }

    private record CertData(KeyPair keyPair, Certificate certificate) {
    }

    // Same as org.opendaylight.netconf.server.ServerTransportInitializer but with explicit fireChannelActive()
    private record TestNetconfServerInitializer(NetconfServerSessionNegotiatorFactory negotiatorFactory)
        implements TransportChannelListener<TransportChannel> {

        @Override
        public void onTransportChannelEstablished(final TransportChannel channel) {
            LOG.debug("Call-Home client's transport channel {} established", channel);
            final var nettyChannel = channel.channel();
            new AbstractChannelInitializer<NetconfServerSession>() {
                @Override
                protected void initializeSessionNegotiator(final Channel ch,
                    final Promise<NetconfServerSession> promise) {
                    ch.pipeline()
                        .addLast(NETCONF_SESSION_NEGOTIATOR, negotiatorFactory.getSessionNegotiator(ch, promise));
                }
            }.initialize(nettyChannel, nettyChannel.eventLoop().newPromise());
            // below line is required
            nettyChannel.pipeline().fireChannelActive();
        }

        @Override
        public void onTransportChannelFailed(final Throwable cause) {
            LOG.error("Call-Home client's transport channel failed", cause);
        }
    }
}
