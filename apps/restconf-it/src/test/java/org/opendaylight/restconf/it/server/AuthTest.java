/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AuthTest extends AbstractE2ETest {
    private static List<String> targets() {
        return List.of("/rests/data",
            "/rests/operations",
            "/rests/yang-library-version",
            "/.well-known/host-meta",
            "/rests/modules/network-topology?revision=2013-10-21");
    }

    private static Stream<Arguments> targetsAndVersions() {
        return targets().stream()
            .flatMap(uri -> Stream.of(ProtocolVersion.values()).map(version -> Arguments.of(uri, version)));
    }

    @ParameterizedTest
    @MethodSource("targetsAndVersions")
    void authorized(final String uri, final ProtocolVersion version) throws Exception {
        final var response = invokeRequest(HttpMethod.GET, uri, version);
        assertResponse(response, HttpResponseStatus.OK);
    }

    @ParameterizedTest
    @MethodSource("targetsAndVersions")
    void unauthorized(final String uri, final ProtocolVersion version) throws Exception {
        final var request = buildRequest(HttpMethod.GET, uri, APPLICATION_JSON, null, null);
        final var response = invokeRequest(request, invalidClientStackGrouping(version),
            version == ProtocolVersion.HTTP_2);
        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void testStreamAuthorized(final ProtocolVersion version) throws Exception {
        final var response = invokeRequest(HttpMethod.GET,
            "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + createStream(version), version);
        assertResponse(response, HttpResponseStatus.OK);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void testStreamUnauthorized(final ProtocolVersion version) throws Exception {
        final var stream = createStream(version);
        final var request = buildRequest(HttpMethod.GET,
            "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + stream,
            APPLICATION_JSON, null, null);
        final var response = invokeRequest(request, invalidClientStackGrouping(version),
            version == ProtocolVersion.HTTP_2);
        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
    }

    private String createStream(final ProtocolVersion version) throws Exception {
        final var response = invokeRequest(HttpMethod.POST,
            "/rests/operations/sal-remote:create-data-change-event-subscription", version,
            APPLICATION_JSON,
            """
                {
                    "input": {
                        "path": "/example-jukebox:jukebox/library/artist[name='artist']/album[name='album']",
                        "sal-remote-augment:datastore": "CONFIGURATION",
                        "sal-remote-augment:scope": "ONE"
                    }
                }
                """);
        assertEquals(HttpResponseStatus.OK, response.status());
        return extractStreamNameJson(response.content().toString(StandardCharsets.UTF_8));
    }

    private static String extractStreamNameJson(final String content) {
        final var json = new JSONObject(content, jsonParserConfiguration());
        return json.getJSONObject("sal-remote:output").getString("stream-name");
    }

    private static void assertResponse(final FullHttpResponse response, final HttpResponseStatus expectedStatus) {
        assertNotNull(response);
        assertEquals(expectedStatus, response.status());
    }
}
