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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StreamsHttp2E2ETest extends AbstractHttp2E2ETest {
    @Override
    @AfterEach
    protected void afterEach() throws Exception {
        if (clientStreamService != null) {
            clientStreamService = null;
        }
        if (streamControl != null) {
            streamControl = null;
        }
        super.afterEach();
    }

    @Test
    void dataChangeEventStreamJsonTest() throws Exception {
        // init parent data
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri("/rests/data/example-jukebox:jukebox"))
            .PUT(HttpRequest.BodyPublishers.ofString("""
                {
                    "example-jukebox:jukebox": {
                        "library": {
                            "artist": [{
                                "name": "artist",
                                "album": []
                            }]
                        }
                    }
                }"""))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());

        final var status = response.statusCode();
        assertTrue(status == HttpResponseStatus.OK.code() || status == HttpResponseStatus.CREATED.code());

        // Create data change notification stream for a node in configuration datastore
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri("/rests/operations/sal-remote:create-data-change-event-subscription"))
            .POST(HttpRequest.BodyPublishers.ofString("""
                {
                    "input": {
                        "path": "/example-jukebox:jukebox/library/artist[name='artist']/album[name='album']",
                        "sal-remote-augment:datastore": "CONFIGURATION",
                        "sal-remote-augment:scope": "ONE"
                    }
                }
                """))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());

        final var json = new JSONObject(response.body(), JSON_PARSER_CONFIGURATION);
        final var streamName = json.getJSONObject("sal-remote:output").getString("stream-name");
        assertNotNull(streamName, "Stream name is undefined");

        // get stream URL from restconf-state
        final var streamUrl = getStreamUrlJson(streamName);
        assertNotNull(streamUrl, "Stream URL not found");

    }
}
