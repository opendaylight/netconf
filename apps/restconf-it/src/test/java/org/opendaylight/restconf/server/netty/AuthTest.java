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
import static org.opendaylight.restconf.server.netty.StreamsE2ETest.extractStreamNameJson;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler;


class AuthTest extends AbstractE2ETest {
    private static final String WRONG_PASSWORD = "wrong_password";

    private static List<String> targets() {
        return List.of("rests/data", "rests/operations", ".well-known/host-meta",
            "rests/modules/network-topology?revision=2013-10-21");
    }

    @BeforeAll
    static void setupAuth() {
        //TODO
    }

    @ParameterizedTest
    @MethodSource("targets")
    void authorized(String uri) throws Exception {
        final var response = invokeRequest("/" + uri, basicAuthHeader(USERNAME, PASSWORD));
        assertResponse(response, HttpResponseStatus.OK);

    }

    @ParameterizedTest
    @MethodSource("targets")
    void unauthorized(String uri) throws Exception {
        final var response = invokeRequest("/" + uri, basicAuthHeader(USERNAME, WRONG_PASSWORD));
        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
    }

    @Test
    void testStreamAuthorized() throws Exception {
        final var response =
            invokeRequest("/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + createStream(),
                basicAuthHeader(USERNAME, PASSWORD));
        assertResponse(response, HttpResponseStatus.OK);
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

    static void assertResponse(final FullHttpResponse response, final HttpResponseStatus expectedStatus) {
        assertNotNull(response);
        assertEquals(expectedStatus, response.status());
    }

    static String basicAuthHeader(final String username, final String password) {
        return "Basic: " + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
