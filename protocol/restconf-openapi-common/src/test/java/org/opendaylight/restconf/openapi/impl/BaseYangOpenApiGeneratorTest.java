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

    @ParameterizedTest
    @MethodSource("proxyHeaders")
    void testResolveScheme(final String expected, final String uriString, final String forwarded,
            final String forwardedProtoHeader) throws Exception {
        final var openApiGenerator = new BaseYangOpenApiGenerator(schemaService) {};
        assertEquals(expected, openApiGenerator.resolveScheme(
            new URI(uriString), forwarded, forwardedProtoHeader));
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
            Arguments.of(HTTP, uriHttps, "PROTO=Http", null),
            Arguments.of(HTTPS, uriHttp, "for=192.0.2.60; proto=\"HTtpS\"; host=example.com", null),

            // xForwardedProto header
            Arguments.of(HTTPS, uriHttp, null, HTTPS),
            Arguments.of(HTTP, uriHttps, null, HTTP),
            Arguments.of(HTTPS, uriHttp, null, " HTTPS "),
            Arguments.of(HTTP, uriHttp, null, " "),
            Arguments.of(HTTPS, uriHttp, "for=192.168.1.1; host=example.com", HTTPS),
            Arguments.of(HTTP, uriHttp, "for=192.168.1.1; host=example.com", null),

            // Both headers
            Arguments.of(HTTPS, uriHttp, "proto=https", HTTP)
        );
    }
}
