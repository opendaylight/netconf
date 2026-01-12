/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static java.util.stream.Collectors.toSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.restconf.it.server.AbstractE2ETest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

public class AbstractHttp2E2ETest extends AbstractE2ETest {
    protected HttpClient http2Client;

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        super.beforeEach();
        http2Client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        USERNAME,
                        PASSWORD.toCharArray());
                }
            })
            .build();
    }

    @AfterEach
    @Override
    protected void afterEach() throws Exception {
        super.afterEach();
        http2Client.close();
    }

    @Override
    protected void assertContentJson(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(getRequestUri))
            .GET()
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertContentJson(response, expectedContent);
    }

    protected static void assertContentJson(final HttpResponse<String> response, final String expectedContent) {
        JSONAssert.assertEquals(expectedContent, response.body(), JSONCompareMode.LENIENT);
    }

    @Override
    protected void assertContentXml(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(getRequestUri))
            .GET()
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_XML)
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_XML)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertContentXml(response, expectedContent);
    }

    protected static void assertContentXml(final HttpResponse<String> response, final String expectedContent) {
        assertThat(response.body(), isSimilarTo(expectedContent).ignoreComments().ignoreWhitespace()
            .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName)));
    }

    protected static void assertErrorResponseJson(final HttpResponse<String> response,
            final ErrorType expectedErrorType, final ErrorTag expectedErrorTag) {
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.statusCode());
        final var json = new JSONObject(response.body(), JSON_PARSER_CONFIGURATION);
        final var error = json.getJSONObject("errors").getJSONArray("error").getJSONObject(0);
        assertNotNull(error);
        assertEquals(expectedErrorType.elementBody(), error.getString("error-type"));
        assertEquals(expectedErrorTag.elementBody(), error.getString("error-tag"));
        assertNotNull(error.getString("error-message"));
    }

    protected static void assertErrorResponseXml(final HttpResponse<String> response, final ErrorType expectedErrorType,
            final ErrorTag expectedErrorTag) {
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        final var content = response.body();
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.statusCode());
        assertThat(content, hasXPath("/r:errors/r:error/r:error-message",
            not(emptyOrNullString())).withNamespaceContext(NS_CONTEXT));
        assertThat(content, hasXPath("/r:errors/r:error/r:error-type",
            equalTo(expectedErrorType.elementBody())).withNamespaceContext(NS_CONTEXT));
        assertThat(content, hasXPath("/r:errors/r:error/r:error-tag",
            equalTo(expectedErrorTag.elementBody())).withNamespaceContext(NS_CONTEXT));
    }

    @Override
    protected void assertOptions(final String uri, final Set<String> methods) throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(uri))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertOptionsResponse(response, methods);
    }

    protected static void assertOptionsResponse(final HttpResponse<String> response, final Set<String> methods) {
        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertHeaderValue(response, HttpHeaderNames.ALLOW.toString(), methods);
    }

    protected static void assertHeaderValue(final HttpResponse<String> response, final String headerName,
        final Set<String> expectedValues) {
        final String headerValue = response.headers()
            .firstValue(headerName)
            .orElseThrow();
        assertEquals(expectedValues, COMMA_SPLITTER.splitToStream(headerValue).collect(toSet())
        );
    }

    @Override
    protected void assertHead(final String uri, final String mediaType) throws Exception {
        final var getResponse = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(uri))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), mediaType)
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), mediaType)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), getResponse.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, getResponse.version());
        assertFalse(getResponse.body().isEmpty());

        final var headResponse = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(uri))
            .HEAD()
            .header(HttpHeaderNames.ACCEPT.toString(), mediaType)
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), mediaType)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), headResponse.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, headResponse.version());
        assertEquals(0, headResponse.body().length());

        assertEquals(normalizeHeaders(getResponse), normalizeHeaders(headResponse));
    }

    private static Map<String, List<String>> normalizeHeaders(HttpResponse<String> response) {
        return response.headers().map().entrySet().stream()
            .filter(e -> !e.getKey().equalsIgnoreCase("content-length"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue
            ));
    }

    protected URI createUri(final String path) throws URISyntaxException {
        return new URI("http://" + host + path);
    }
}
