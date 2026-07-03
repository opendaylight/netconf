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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BaseYangOpenApiGeneratorTest {
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    @ParameterizedTest
    @MethodSource("proxyHeaders")
    void testResolveScheme(final String expected, final String uriString, final String forwarded,
            final String forwardedProtoHeader) throws Exception {
        assertEquals(expected, BaseYangOpenApiGenerator.resolveScheme(
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
            Arguments.of(HTTPS, uriHttp, "for=192.0.2.60; proto=\"https\"; host=example.com", null),

            // xForwardedProto header
            Arguments.of(HTTPS, uriHttp, null, HTTPS),
            Arguments.of(HTTP, uriHttps, null, HTTP),
            Arguments.of(HTTPS, uriHttp, null, " HTTPS "),
            Arguments.of(HTTP, uriHttp, null, " "),

            // Both headers
            Arguments.of(HTTPS, uriHttp, "proto=https", HTTP)
        );
    }
}
