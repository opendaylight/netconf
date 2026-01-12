/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


class AuthHttp2Test extends AbstractHttp2E2ETest {

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
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(new URI("http://" + host + uri))
            .GET()
            .build(), HttpResponse.BodyHandlers.ofString());
        assertResponse(response, HttpResponseStatus.OK);
        assertEquals(HttpClient.Version.HTTP_2, response.version());
    }

    @ParameterizedTest
    @MethodSource("targets")
    void unauthorized(final String uri) throws Exception {
        // Setup client without authorization
        final var invalidHttp2Client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

        // Send request with incorrect credentials in authorization header
        final var request = HttpRequest.newBuilder()
            .uri(new URI("http://" + host + uri))
            .GET()
            .header(HttpHeaderNames.AUTHORIZATION.toString(), "Basic dXNlcm5hbWU6d3JvbmdfcGFzc3dvcmQ=")
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build();

        var response = invalidHttp2Client.send(request, HttpResponse.BodyHandlers.ofString());
        invalidHttp2Client.close();
        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
        assertEquals(HttpClient.Version.HTTP_2, response.version());
    }

    @Disabled
    @Test
    void testStreamAuthorized() throws Exception {
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(new URI("http://" + host + "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream="
                         + createStream()))
            .GET()
            .build(), HttpResponse.BodyHandlers.ofString());
        assertResponse(response, HttpResponseStatus.OK);
        assertEquals(HttpClient.Version.HTTP_2, response.version());
    }

    @Disabled
    @Test
    void testStreamUnauthorized() throws Exception {
        // Setup client without authorization
        final var invalidHttp2Client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

        // Send request with incorrect credentials in authorization header
        final var request = HttpRequest.newBuilder()
            .uri(new URI("http://" + host + "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream="
                         + createStream()))
            .GET()
            .header(HttpHeaderNames.AUTHORIZATION.toString(), "Basic dXNlcm5hbWU6d3JvbmdfcGFzc3dvcmQ=")
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build();

        var response = invalidHttp2Client.send(request, HttpResponse.BodyHandlers.ofString());
        assertResponse(response, HttpResponseStatus.UNAUTHORIZED);
        assertEquals(HttpClient.Version.HTTP_2, response.version());
    }

    private String createStream() throws Exception {

        final var request = HttpRequest.newBuilder()
            .uri(new URI("http://" + host + "/rests/operations/sal-remote:create-data-change-event-subscription"))
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
        final var response = http2Client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        return extractStreamNameJson(response.body());
    }

    private static String extractStreamNameJson(final String content) {
        final var json = new JSONObject(content, JSON_PARSER_CONFIGURATION);
        return json.getJSONObject("sal-remote:output").getString("stream-name");
    }

    private static void assertResponse(final HttpResponse<String> response, final HttpResponseStatus expectedStatus) {
        assertNotNull(response);
        assertEquals(expectedStatus.code(), response.statusCode());
    }
}
