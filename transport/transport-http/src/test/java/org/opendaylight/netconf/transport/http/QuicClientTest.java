/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.crypto.CMSCertificateParser;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.client.identity.auth.type.Basic;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore.certs.grouping.inline.or.truststore.Inline;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.http3.client.grouping.quic.under.http.Quic;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.http3.client.grouping.quic.under.http.quic.quic.QuicClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.quic.common.rev260901.Varint;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;

class QuicClientTest {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8443;
    private static final Uint64 INITIAL_MAX_DATA = Uint64.valueOf(4L * 1024 * 1024);
    private static final Uint64 INITIAL_MAX_STREAM_DATA_BIDI = Uint64.valueOf(256L * 1024);
    private static final Uint32 INITIAL_MAX_STREAMS_BIDI = Uint32.valueOf(100);
    private static final Uint64 MAX_IDLE_TIMEOUT = Uint64.valueOf(30000);
    private static final QuicClientParametersBuilder QUIC_CLIENT_PARAMETERS = new QuicClientParametersBuilder()
        .setInitialMaxData(new Varint(INITIAL_MAX_DATA))
        .setInitialMaxStreamDataBidiRemote(new Varint(INITIAL_MAX_STREAM_DATA_BIDI))
        .setInitialMaxStreamsBidi(INITIAL_MAX_STREAMS_BIDI)
        .setMaxIdleTimeout(new Varint(MAX_IDLE_TIMEOUT));

    private static BootstrapFactory bootstrapFactory;

    @BeforeAll
    static void beforeAll() {
        bootstrapFactory = new BootstrapFactory("QuicClientTest", 0);
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
    }

    @Test
    void clientTransportQuicWithAuth() throws Exception {
        final var cert = TestUtils.generateX509CertData("RSA").certificate();

        final var transport = ConfigUtils.clientTransportQuic(HOST, PORT, cert, QUIC_CLIENT_PARAMETERS.build(),
            "user", "pass");

        final var quic = assertInstanceOf(Quic.class, transport).getQuic();

        final var udpParams = quic.getUdpClientParameters();
        assertEquals(HOST, udpParams.getRemoteAddress().getIpAddress().getIpv4Address().getValue());
        assertEquals(PORT, udpParams.getRemotePort().getValue().toJava());

        final var quicParams = quic.getQuicClientParameters();
        assertEquals(INITIAL_MAX_DATA, quicParams.getInitialMaxData().getValue());
        assertEquals(INITIAL_MAX_STREAM_DATA_BIDI, quicParams.getInitialMaxStreamDataBidiRemote().getValue());
        assertEquals(INITIAL_MAX_STREAMS_BIDI, quicParams.getInitialMaxStreamsBidi());
        assertEquals(MAX_IDLE_TIMEOUT, quicParams.getMaxIdleTimeout().getValue());

        // Follows the same nested inline-or-truststore shape as HTTPClient.collectTrustCertificates() reads on the
        // production side, to verify clientTransportQuic() actually wrote the certificate where a real client
        // would look for it
        final var eeCerts = quic.getTlsClientParameters().getServerAuthentication().getEeCerts();
        final var inline = assertInstanceOf(Inline.class, eeCerts.getInlineOrTruststore());
        final var storedCertificate = inline.getInlineDefinition().nonnullCertificate().values().iterator().next();
        final var parsedCert = CMSCertificateParser.parseCertificate(storedCertificate.requireCertData());
        assertEquals(cert, parsedCert);

        final var basic = assertInstanceOf(Basic.class,
            quic.getHttpClientParameters().getClientIdentity().getAuthType()).getBasic();
        assertEquals("user", basic.getUserId());
    }

    @Test
    void clientTransportQuicWithoutAuth() throws Exception {
        final var cert = TestUtils.generateX509CertData("RSA");
        final var transport = ConfigUtils.clientTransportQuic(HOST, PORT, cert.certificate(),
            QUIC_CLIENT_PARAMETERS.build(), null, null);

        final var quic = assertInstanceOf(Quic.class, transport).getQuic();
        assertNull(quic.getHttpClientParameters().getClientIdentity());
    }

    /**
     * Verifies that transport built by {@link ConfigUtils#clientTransportQuic} actually connects: drives
     * {@link HTTPClient#connect} (and, through it, the QUIC-specific {@code connectQuic()} path) against a real
     * {@link HTTPServerOverQuic} listener, rather than only checking the shape of the built config object.
     */
    @Test
    void connectQuicToRealServer() throws Exception {
        final var loopback = InetAddress.getLoopbackAddress();
        // Bind-then-close to obtain a free UDP port, same trick used to force a bind conflict in
        // HTTPServerQuicBindFailureTest.
        final int port;
        try (var probe = new DatagramSocket(new InetSocketAddress(loopback, 0))) {
            port = probe.getLocalPort();
        }

        final var certData = TestUtils.generateX509CertData("RSA");
        final var serverCase = HTTPServerOverQuic.of(loopback.getHostAddress(), port, certData.certificate(),
            certData.privateKey(), INITIAL_MAX_DATA, INITIAL_MAX_STREAM_DATA_BIDI, INITIAL_MAX_STREAMS_BIDI);

        final var serverListener = new CapturingListener();
        final var server = HTTPServer.listen(serverListener, serverCase).get(5, TimeUnit.SECONDS);
        try {
            final var clientConfig = new HttpClientStackConfiguration(
                ConfigUtils.clientTransportQuic(loopback.getHostAddress(), port, certData.certificate(),
                    INITIAL_MAX_DATA, INITIAL_MAX_STREAM_DATA_BIDI, INITIAL_MAX_STREAMS_BIDI, null, null));

            final var clientListener = new CapturingListener();
            final var client = HTTPClient.connect(clientListener, bootstrapFactory.newBootstrap(), clientConfig,
                false).get(5, TimeUnit.SECONDS);
            try {
                // both ends must have observed the same QUIC connection being established
                assertNotNull(clientListener.established.get(5, TimeUnit.SECONDS));
                assertNotNull(serverListener.established.get(5, TimeUnit.SECONDS));
            } finally {
                client.shutdown().get(5, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(5, TimeUnit.SECONDS);
        }
    }

    /** Captures the {@link HTTPTransportChannel} reported by a successful connect/listen, if any. */
    private static final class CapturingListener implements TransportChannelListener<HTTPTransportChannel> {
        private final CompletableFuture<HTTPTransportChannel> established = new CompletableFuture<>();

        @Override
        public void onTransportChannelEstablished(final HTTPTransportChannel channel) {
            established.complete(channel);
        }

        @Override
        public void onTransportChannelFailed(final Throwable cause) {
            established.completeExceptionally(cause);
        }
    }

}
