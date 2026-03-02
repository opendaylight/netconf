/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http3;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
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
import org.json.JSONObject;
import org.json.JSONParserConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.it.openapi.AbstractOpenApiTest;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class AbstractOpenApiHttp3Test extends AbstractOpenApiTest {
    static final JSONParserConfiguration JSON_PARSER_CONFIGURATION = new JSONParserConfiguration()
        .withStrictMode();

    private static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;
    private static final String TOPOLOGY_URI =
        "/rests/data/network-topology:network-topology/topology=topology-netconf";
    private static final String DEVICE_NODE_URI = TOPOLOGY_URI + "/node=device-sim";
    private static final String DEVICE_STATUS_URI =
        DEVICE_NODE_URI + "/netconf-node-topology:netconf-node?fields=connection-status";
    private static final Uint32 CHUNK_SIZE = Uint32.valueOf(256 * 1024);
    private static final Uint32 FRAME_SIZE = Uint32.valueOf(16 * 1024);
    private static final String ALT_SVC_HEADER = "h3=\":8443\"; ma=3600";
    private static final Uint32 HTTP3_ALT_SVC_MAX_AGE_SECONDS = Uint32.valueOf(3600);
    private static final Uint64 HTTP3_INITIAL_MAX_DATA = Uint64.valueOf(4L * 1024 * 1024);
    private static final Uint64 HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE = Uint64.valueOf(256L * 1024);
    private static final Uint32 HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL = Uint32.valueOf(100);

    protected Http3NettyTestClient client;

    private PrivateKey privateKey;
    private X509Certificate certificate;

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        // transport configuration
        port = randomBindablePort();
        host = localAddress + ":" + port;

        // Certificates
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
        client = new Http3NettyTestClient(localAddress, port, USERNAME, PASSWORD);
    }

    @AfterEach
    @Override
    protected void afterEach() throws Exception {
        client.close();
        super.afterEach();
    }

    @Override
    protected Transport createTransport() {
        return ConfigUtils.serverTransportTls(localAddress, port, certificate, privateKey);
    }

    @Override
    protected NettyEndpointConfiguration createEndpointConfiguration() {
        return new NettyEndpointConfiguration(ERROR_TAG_MAPPING, PrettyPrintParam.FALSE,
            Uint16.ZERO, Uint32.valueOf(1000), "rests", MessageEncoding.JSON, serverStackGrouping(), CHUNK_SIZE,
            FRAME_SIZE, ALT_SVC_HEADER, localAddress, port, certificate, privateKey, HTTP3_ALT_SVC_MAX_AGE_SECONDS,
            HTTP3_INITIAL_MAX_DATA, HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE,
            HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL);
    }

    protected URI createApiUri(final String path) throws URISyntaxException {
        return new URI("https://" + host + API_V3_PATH + path);
    }

    @Override
    protected void assertContentJson(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = client.send(HttpRequest.newBuilder()
            .GET()
            .uri(new URI("https://" + host + getRequestUri))
            .build());
        assertEquals(HttpResponseStatus.OK, response.status());
        final var content = response.content();
        JSONAssert.assertEquals(expectedContent, content, JSONCompareMode.LENIENT);
    }

    @Override
    protected void mountDeviceJson(final int devicePort) throws Exception {
        // validate topology node is defined
        assertContentJson(TOPOLOGY_URI,
            """
                {
                    "network-topology:topology": [{
                        "topology-id":"topology-netconf"
                    }]
                }""");
        final var input = """
            {
               "network-topology:node": [{
                   "node-id": "device-sim",
                   "netconf-node-topology:netconf-node": {
                       "host": "%s",
                       "port": %d,
                       "login-password-unencrypted": {
                           "username": "%s",
                           "password": "%s"
                       },
                       "tcp-only": false
                   }
               }]
            }
            """.formatted(localAddress, devicePort, DEVICE_USERNAME, DEVICE_PASSWORD);
        final var response = client.send(HttpRequest.newBuilder()
            .uri(new URI("https://" + host + TOPOLOGY_URI))
            .POST(HttpRequest.BodyPublishers.ofString(input))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build());
        assertEquals(HttpResponseStatus.CREATED, response.status());
        // wait till connected
        await().atMost(Duration.ofSeconds(50)).pollInterval(Duration.ofMillis(500))
            .until(this::deviceConnectedJson);
    }

    private boolean deviceConnectedJson() throws Exception {
        final var response = client.send(HttpRequest.newBuilder()
            .uri(new URI("https://" + host + DEVICE_STATUS_URI))
            .GET()
            .build());

        assertEquals(HttpResponseStatus.OK, response.status());
        final var json = new JSONObject(response.content(), JSON_PARSER_CONFIGURATION);
        //{
        //  "netconf-node-topology:netconf-node": {
        //    "connection-status": "connected"
        //  }
        //}
        final var status = json.getJSONObject("netconf-node-topology:netconf-node").getString("connection-status");
        return "connected".equals(status);
    }
}
