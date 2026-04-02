/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http3;

import static java.util.stream.Collectors.toSet;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opendaylight.restconf.it.openapi.http3.Http3NettyTestClient.Http3Response;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;

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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.it.openapi.http3.Http3NettyTestClient;
import org.opendaylight.restconf.it.server.AbstractE2ETest;
import org.opendaylight.restconf.it.server.TestEventStreamListener;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.HttpServerListenStackGrouping;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

public abstract class AbstractHttp3E2ETest extends AbstractE2ETest {
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

    @AfterEach
    @Override
    protected void afterEach() throws Exception {
        client.close();
        super.afterEach();
    }

    protected Http3NettyTestClient client() {
        return client;
    }

    @Override
    protected NettyEndpointConfiguration createEndpointConfiguration(
            final HttpServerListenStackGrouping serverStackGrouping) {
        return new NettyEndpointConfiguration(
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000), "rests",
            MessageEncoding.JSON, serverStackGrouping, CHUNK_SIZE, FRAME_SIZE, WRITE_BUFFER_LOW_WATER_MARK,
            WRITE_BUFFER_HIGH_WATER_MARK, ALT_SVC_HEADER, localAddress(), port(), certificate, privateKey,
            HTTP3_ALT_SVC_MAX_AGE_SECONDS, HTTP3_INITIAL_MAX_DATA, HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE,
            HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL);
    }

    @Override
    protected URI getStreamUrlJson(final String streamName) throws Exception {
        // get stream URL from restconf-state
        final var response = client.send(HttpRequest.newBuilder()
            .uri(createUri("/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + streamName))
            .GET()
            .build());
        assertEquals(HttpResponseStatus.OK, response.status());
        return extractStreamUrlJson(response.content());
    }

    protected URI createUri(final String path) throws URISyntaxException {
        return new URI("https://" + host() + path);
    }

    protected static void assertErrorResponseJson(final Http3Response response, final ErrorType expectedErrorType,
        final ErrorTag expectedErrorTag) {
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        final var json = new JSONObject(response.content(), jsonParserConfiguration());
        final var error = json.getJSONObject("errors").getJSONArray("error").getJSONObject(0);
        assertNotNull(error);
        assertEquals(expectedErrorType.elementBody(), error.getString("error-type"));
        assertEquals(expectedErrorTag.elementBody(), error.getString("error-tag"));
        assertNotNull(error.getString("error-message"));
    }

    protected static void assertErrorResponseXml(final Http3Response response, final ErrorType expectedErrorType,
        final ErrorTag expectedErrorTag) {
        final var content = response.content();
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        assertThat(content, hasXPath("/r:errors/r:error/r:error-message",
            not(emptyOrNullString())).withNamespaceContext(NS_CONTEXT));
        assertThat(content, hasXPath("/r:errors/r:error/r:error-type",
            equalTo(expectedErrorType.elementBody())).withNamespaceContext(NS_CONTEXT));
        assertThat(content, hasXPath("/r:errors/r:error/r:error-tag",
            equalTo(expectedErrorTag.elementBody())).withNamespaceContext(NS_CONTEXT));
    }

    protected static void assertContentJson(final Http3Response response, final String expectedContent) {
        final var content = response.content();
        JSONAssert.assertEquals(expectedContent, content, JSONCompareMode.LENIENT);
    }

    @Override
    protected void assertContentJson(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = client.send(HttpRequest.newBuilder()
            .uri(createUri(getRequestUri))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_JSON)
            .build());

        assertEquals(HttpResponseStatus.OK, response.status());
        assertContentJson(response, expectedContent);
    }

    protected static void assertContentXml(final Http3Response response, final String expectedContent) {
        final var content = response.content();
        assertThat(content, isSimilarTo(expectedContent).ignoreComments().ignoreWhitespace()
            .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName)));
    }

    @Override
    protected void assertContentXml(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = client.send(HttpRequest.newBuilder()
            .uri(createUri(getRequestUri))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_XML)
            .build());

        assertEquals(HttpResponseStatus.OK, response.status());
        assertContentXml(response, expectedContent);
    }

    @Override
    protected void assertOptions(final String uri, final Set<String> methods) throws Exception {
        final var response = client.send(HttpRequest.newBuilder()
            .uri(createUri(uri))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build());

        assertOptionsResponse(response, methods);
    }

    protected static void assertOptionsResponse(final Http3Response response, final Set<String> methods) {
        assertEquals(HttpResponseStatus.OK, response.status());
        assertHeaderValue(response, HttpHeaderNames.ALLOW.toString(), methods);
    }

    protected static void assertHeaderValue(final Http3Response response, final String headerName,
        final Set<String> expectedValues) {
        final var headerValue = response.headers().get(headerName).getFirst();
        assertNotNull(headerValue);
        assertEquals(expectedValues, COMMA_SPLITTER.splitToStream(headerValue).collect(toSet()));
    }

    @Override
    protected void assertHead(final String uri, final String mediaType) throws Exception {
        final var getResponse = client.send(HttpRequest.newBuilder()
            .uri(createUri(uri))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), mediaType)
            .build());

        assertEquals(HttpResponseStatus.OK, getResponse.status());
        assertFalse(getResponse.content().isEmpty());

        final var headResponse = client.send(HttpRequest.newBuilder()
            .uri(createUri(uri))
            .HEAD()
            .header(HttpHeaderNames.ACCEPT.toString(), mediaType)
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), mediaType)
            .build());

        assertEquals(HttpResponseStatus.OK, headResponse.status());
        assertEquals(0, headResponse.content().length());

        assertEquals(normalizeHeaders(getResponse), normalizeHeaders(headResponse));
    }

    private static Map<String, String> normalizeHeaders(final Http3Response response) {
        return response.headers().asMap().entrySet().stream()
            .filter(e -> !e.getKey().equalsIgnoreCase("content-length"))
            .collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().iterator().next()
            ));
    }

    protected TestEventStreamListener startStream(final URI uri) throws Exception {
        final var listener = new TestEventStreamListener();
        final var control = client.listenToStream(uri, listener);
        await().atMost(Duration.ofSeconds(2)).until(listener::started);
        addStreamControl(control);

        return listener;
    }
}
