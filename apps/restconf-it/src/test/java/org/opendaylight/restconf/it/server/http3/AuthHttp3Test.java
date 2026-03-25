/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opendaylight.restconf.it.openapi.http3.Http3NettyTestClient.Http3Response;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpRequest;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.restconf.it.openapi.http3.Http3NettyTestClient;

class AuthHttp3Test extends AbstractHttp3E2ETest {
    private static List<String> targets() {
        return List.of("/rests/data",
            "/rests/operations",
            "/rests/yang-library-version",
            "/.well-known/host-meta",
            "/rests/modules/network-topology?revision=2013-10-21");
    }

    @ParameterizedTest
    @MethodSource("targets")
    void authorized(final String uri) throws Exception {
        final var response = client().send(HttpRequest.newBuilder()
            .uri(createUri(uri))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_JSON)
            .build());

        assertResponse(response, HttpResponseStatus.OK);
    }

    @ParameterizedTest
    @MethodSource("targets")
    void unauthorized(final String uri) throws Exception {
        // Setup client without authorization
        final var clientNoAuth = new Http3NettyTestClient(localAddress(), port());

        // Send request with incorrect credentials in authorization header
        final var request = HttpRequest.newBuilder()
            .uri(createUri(uri))
            .GET()
            .header(HttpHeaderNames.AUTHORIZATION.toString(), "Basic dXNlcm5hbWU6d3JvbmdfcGFzc3dvcmQ=")
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_JSON)
            .build();
        final var response = clientNoAuth.send(request);
        clientNoAuth.close();

        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
    }

    @Test
    void testStreamAuthorized() throws Exception {
        final var response = client().send(HttpRequest.newBuilder()
            .uri(createUri("/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + createStream()))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_JSON)
            .build());
        assertResponse(response, HttpResponseStatus.OK);
    }

    @Test
    void testStreamUnauthorized() throws Exception {
        // Setup client without authorization
        final var clientNoAuth = new Http3NettyTestClient(localAddress(), port());

        // Send request with incorrect credentials in authorization header
        final var request = HttpRequest.newBuilder()
            .uri(createUri("/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + createStream()))
            .GET()
            .header(HttpHeaderNames.AUTHORIZATION.toString(), "Basic dXNlcm5hbWU6d3JvbmdfcGFzc3dvcmQ=")
            .header(HttpHeaderNames.ACCEPT.toString(), APPLICATION_JSON)
            .build();
        final var response = clientNoAuth.send(request);

        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
    }

    private String createStream() throws Exception {
        final var request = HttpRequest.newBuilder()
            .uri(createUri("/rests/operations/sal-remote:create-data-change-event-subscription"))
            .POST(HttpRequest.BodyPublishers.ofString(
                """
                    {
                        "input": {
                            "path": "/example-jukebox:jukebox/library/artist[name='artist']/album[name='album']",
                            "sal-remote-augment:datastore": "CONFIGURATION",
                            "sal-remote-augment:scope": "ONE"
                        }
                    }
                    """))
            .header(HttpHeaderNames.AUTHORIZATION.toString(), "Basic dXNlcm5hbWU6d3JvbmdfcGFzc3dvcmQ=")
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build();
        final var response = client().send(request);

        assertEquals(HttpResponseStatus.OK, response.status());
        return extractStreamNameJson(response.content());
    }

    private static String extractStreamNameJson(final String content) {
        final var json = new JSONObject(content, jsonParserConfiguration());
        return json.getJSONObject("sal-remote:output").getString("stream-name");
    }

    private static void assertResponse(final Http3Response response, final HttpResponseStatus expectedStatus) {
        assertNotNull(response);
        assertEquals(expectedStatus, response.status());
    }
}
