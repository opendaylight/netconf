/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.transport.crypto.CMSCertificateParser;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.client.identity.auth.type.Basic;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore.certs.grouping.inline.or.truststore.Inline;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.http3.client.grouping.quic.under.http.Quic;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;

class ConfigUtilsTest {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8443;
    private static final Uint64 INITIAL_MAX_DATA = Uint64.valueOf(4L * 1024 * 1024);
    private static final Uint64 INITIAL_MAX_STREAM_DATA_BIDI = Uint64.valueOf(256L * 1024);
    private static final Uint32 INITIAL_MAX_STREAMS_BIDI = Uint32.valueOf(100);

    @Test
    void clientTransportQuicWithAuth() throws Exception {
        final var cert = TestUtils.generateX509CertData("RSA").certificate();

        final var transport = ConfigUtils.clientTransportQuic(HOST, PORT, cert, INITIAL_MAX_DATA,
            INITIAL_MAX_STREAM_DATA_BIDI, INITIAL_MAX_STREAMS_BIDI, "user", "pass");

        final var quic = assertInstanceOf(Quic.class, transport).getQuic();

        final var udpParams = quic.getUdpClientParameters();
        assertEquals(HOST, udpParams.getRemoteAddress().getIpAddress().getIpv4Address().getValue());
        assertEquals(PORT, udpParams.getRemotePort().getValue().toJava());

        final var quicParams = quic.getQuicClientParameters();
        assertEquals(INITIAL_MAX_DATA, quicParams.getInitialMaxData().getValue());
        assertEquals(INITIAL_MAX_STREAM_DATA_BIDI, quicParams.getInitialMaxStreamDataBidiRemote().getValue());
        assertEquals(INITIAL_MAX_STREAMS_BIDI, quicParams.getInitialMaxStreamsBidi());

        // mirrors HTTPClient.collectTrustCertificates()'s own read-side of this exact structure
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
        final var transport = ConfigUtils.clientTransportQuic(HOST, PORT, cert.certificate(), INITIAL_MAX_DATA,
            INITIAL_MAX_STREAM_DATA_BIDI, INITIAL_MAX_STREAMS_BIDI, null, null);

        final var quic = assertInstanceOf(Quic.class, transport).getQuic();
        assertNull(quic.getHttpClientParameters().getClientIdentity());
    }
}
