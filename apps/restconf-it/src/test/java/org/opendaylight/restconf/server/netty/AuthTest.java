/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


class AuthTest extends AbstractE2ETest {
    private static final String WRONG_PASSWORD = "wrong_password";
    private static final String PASSWORD = "pa$$w0Rd";

    private static String password;

    private static List<String> targets() {
        return List.of("rests/data", "rests/operations", ".well-known/host-meta",
            "rests/modules/network-topology?revision=2013-10-21");
    }

    @ParameterizedTest
    @MethodSource("targets")
    void authorized(String uri) throws Exception {
        password = PASSWORD;
        final var response = invokeRequest(HttpMethod.GET, "/" + uri);
        assertResponse(response, HttpResponseStatus.OK);

    }

    @ParameterizedTest
    @MethodSource("targets")
    void unauthorized(String uri) throws Exception {
        password = WRONG_PASSWORD;
        final var response = invokeRequest(HttpMethod.GET, "/" + uri);
        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
    }

    @Test
    void testStreamAuthorized() throws Exception {
        password = PASSWORD;
        final var response =
            invokeRequest(HttpMethod.GET,
                "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + createStream());
        assertResponse(response, HttpResponseStatus.OK);
    }

    @Test
    void testStreamUnauthorized() throws Exception {
        password = PASSWORD;
        final var stream = createStream();
        password = WRONG_PASSWORD;
        final var response = invokeRequest(HttpMethod.GET,
            "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + stream);
        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
    }

    private String createStream() throws Exception {
        var response = invokeRequest(HttpMethod.POST,
            "/rests/operations/sal-remote:create-data-change-event-subscription",
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
        final var json = new JSONObject(content);
        return json.getJSONObject("sal-remote:output").getString("stream-name");
    }

    static void assertResponse(final FullHttpResponse response, final HttpResponseStatus expectedStatus) {
        assertNotNull(response);
        assertEquals(expectedStatus, response.status());
    }

    @Override
    String getPassword() {
        return password;
    }
}
