/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;

@ExtendWith(MockitoExtension.class)
class BaseYangOpenApiGeneratorTest {
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    @Mock
    private DOMSchemaService schemaService;

    /**
     * Verifies {@link BaseYangOpenApiGenerator#resolveScheme(URI, String, String)} priority order: the first
     * comma-separated element of the RFC 7239 {@code Forwarded} header wins if it carries a {@code proto} value,
     * otherwise the leftmost entry of the legacy {@code X-Forwarded-Proto} header is used, otherwise the request
     * URI's own scheme is used. A {@code proto} present only in a later {@code Forwarded} element is ignored.
     * Only {@code http} and {@code https} are accepted from the headers; any other value is ignored and resolution
     * falls through to the next source.
     */
    @ParameterizedTest
    @MethodSource("proxyHeaders")
    void testResolveScheme(final String expected, final String uriString, final String forwardedHeader,
            final String forwardedProtoHeader) throws Exception {
        final var openApiGenerator = new BaseYangOpenApiGenerator(schemaService) {};
        assertEquals(expected, openApiGenerator.resolveScheme(
            new URI(uriString), forwardedHeader, forwardedProtoHeader));
    }

    private static Stream<Arguments> proxyHeaders() {
        final var uriHttp = "http://localhost:8181/openapi/api/v3/";
        final var uriHttps = "https://localhost:8181/openapi/api/v3/";

        return Stream.of(
            // Without headers
            Arguments.of(HTTP, uriHttp, null, null),
            Arguments.of(HTTPS, uriHttps, null, null),

            // RFC 7239 Forwarded header
            Arguments.of(HTTPS, uriHttp, "proto=https", null),
            Arguments.of(HTTPS, uriHttp, "for=192.168.1.1; proto=https; host=example.com", null),
            Arguments.of(HTTPS, uriHttp, "For=192.168.1.1;Proto=https", null),
            Arguments.of(HTTPS, uriHttp, "for=192.168.1.1; proto=\"https\"; host=example.com", null),
            Arguments.of(HTTPS, uriHttp, "for=192.168.1.1;proto=https, for=192.168.1.2;proto=http", null),
            Arguments.of(HTTP, uriHttps, "for=192.168.1.1;proto=http, for=192.168.1.2;proto=https", null),
            Arguments.of(HTTPS, uriHttp, "proto=HTTPS", null),
            Arguments.of(HTTPS, uriHttp, "PROTO=Https", null),

            // Proto missing from the first element is not looked up in later elements
            Arguments.of(HTTP, uriHttp, "for=192.168.1.1, for=192.168.1.2;proto=https", null),

            // Quoted IPv6 node identifiers (RFC 7239 section 6)
            Arguments.of(HTTPS, uriHttp, "for=\"[2001:db8::1]\";proto=https", null),
            Arguments.of(HTTPS, uriHttp, "for=\"[2001:db8::1]:4711\"; proto=https; host=example.com", null),
            Arguments.of(HTTPS, uriHttp, "for=\"[2001:db8::1]\";proto=https, for=192.168.1.2;proto=http", null),
            Arguments.of(HTTP, uriHttp, "for=\"[2001:db8::1]\"", null),

            // Malformed Forwarded header falls back to X-Forwarded-Proto or the URI scheme
            Arguments.of(HTTP, uriHttp, "proto=", null),
            Arguments.of(HTTP, uriHttp, "proto==https", null),
            Arguments.of(HTTP, uriHttp, "garbage", null),
            Arguments.of(HTTPS, uriHttp, "proto=", HTTPS),
            // An unterminated quote is tolerated
            Arguments.of(HTTPS, uriHttp, "proto=\"https", null),
            // A comma inside a quoted value truncates the first element, hiding its proto
            Arguments.of(HTTP, uriHttp, "for=\"_gazonk, oops\";proto=https", null),

            // Malformed X-Forwarded-Proto header falls back to the URI scheme
            Arguments.of(HTTP, uriHttp, null, ",https"),

            // Schemes other than http/https are not accepted, resolution falls through to the next source
            Arguments.of(HTTP, uriHttp, "proto=ftp", null),
            Arguments.of(HTTPS, uriHttp, "proto=ftp", HTTPS),
            Arguments.of(HTTP, uriHttp, null, "javascript"),
            Arguments.of(HTTP, uriHttp, null, "\"><img src=x"),

            // X-Forwarded-Proto header
            Arguments.of(HTTPS, uriHttp, null, HTTPS),
            Arguments.of(HTTP, uriHttps, null, HTTP),
            Arguments.of(HTTPS, uriHttp, null, " HTTPS "),
            Arguments.of(HTTP, uriHttp, null, " "),
            Arguments.of(HTTPS, uriHttp, "for=192.168.1.1; host=example.com", HTTPS),
            Arguments.of(HTTP, uriHttp, "for=192.168.1.1; host=example.com", null),
            Arguments.of(HTTPS, uriHttp, null, "https, http"),
            Arguments.of(HTTP, uriHttps, null, "http, https"),

            // Both headers
            Arguments.of(HTTPS, uriHttp, "proto=https", HTTP)
        );
    }
}
