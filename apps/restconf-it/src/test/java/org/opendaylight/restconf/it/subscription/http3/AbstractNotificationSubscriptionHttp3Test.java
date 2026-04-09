/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription.http3;

import static org.awaitility.Awaitility.await;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.it.openapi.http3.Http3NettyTestClient;
import org.opendaylight.restconf.it.server.TestEventStreamListener;
import org.opendaylight.restconf.it.subscription.AbstractNotificationSubscriptionTest;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.HttpServerListenStackGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;

public abstract class AbstractNotificationSubscriptionHttp3Test extends AbstractNotificationSubscriptionTest {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0Rd";
    private static final Uint32 CHUNK_SIZE = Uint32.valueOf(256 * 1024);
    private static final Uint32 FRAME_SIZE = Uint32.valueOf(16 * 1024);
    private static final String ALT_SVC_HEADER = "h3=\":8443\"; ma=3600";
    private static final Uint32 HTTP3_ALT_SVC_MAX_AGE_SECONDS = Uint32.valueOf(3600);
    private static final Uint64 HTTP3_INITIAL_MAX_DATA = Uint64.valueOf(4L * 1024 * 1024);
    private static final Uint64 HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE = Uint64.valueOf(256L * 1024);
    private static final Uint32 HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL = Uint32.valueOf(100);
    private static final Uint32 WRITE_BUFFER_LOW_WATER_MARK = Uint32.valueOf(32 * 1024);
    private static final Uint32 WRITE_BUFFER_HIGH_WATER_MARK = Uint32.valueOf(64 * 1024);

    private PrivateKey privateKey;
    private X509Certificate certificate;
    private Http3NettyTestClient client;

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        // Create certificates
        final var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        final var keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        final var x500Name = new X500Name("CN=TestCertificate");
        final var now = Instant.now();
        final var certBuilder =  new JcaX509v3CertificateBuilder(x500Name, BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now), Date.from(now.plus(Duration.ofDays(1))), x500Name, keyPair.getPublic());
        final var signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        certificate = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

        super.beforeEach();

        // Setup HTTP/3 client
        client = new Http3NettyTestClient(localAddress(), port(), USERNAME, PASSWORD);
    }

    @Override
    protected NettyEndpointConfiguration createEndpointConfiguration(
        final HttpServerListenStackGrouping serverStackGrouping) {
        return new NettyEndpointConfiguration(
            ErrorTagMapping.RFC8040, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000), "restconf",
            MessageEncoding.JSON, serverStackGrouping, CHUNK_SIZE, FRAME_SIZE, WRITE_BUFFER_LOW_WATER_MARK,
            WRITE_BUFFER_HIGH_WATER_MARK, ALT_SVC_HEADER, localAddress(), port(), certificate, privateKey,
            HTTP3_ALT_SVC_MAX_AGE_SECONDS, HTTP3_INITIAL_MAX_DATA, HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE,
            HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL);
    }

    protected Http3NettyTestClient client() {
        return client;
    }

    protected URI createUri(final String path) throws URISyntaxException {
        return new URI("https://" + host() + path);
    }

    @Override
    protected TestEventStreamListener startSubscriptionStream(final String subscriptionId) throws Exception {
        final var listener = new TestEventStreamListener();
        client.listenToStream(createUri("/subscriptions/" + subscriptionId), listener);
        await().atMost(Duration.ofSeconds(2)).until(listener::started);

        return listener;
    }
}
