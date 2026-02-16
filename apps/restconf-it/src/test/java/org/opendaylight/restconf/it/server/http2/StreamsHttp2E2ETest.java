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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.it.server.AbstractE2ETest;

class StreamsHttp2E2ETest extends AbstractE2ETest {
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
        var response = invokeRequest(HttpMethod.PUT,
            "/rests/data/example-jukebox:jukebox",
            APPLICATION_JSON,
            """
                {
                    "example-jukebox:jukebox": {
                        "library": {
                            "artist": [{
                                "name": "artist",
                                "album": []
                            }]
                        }
                    }
                }""");
        final var status = response.status();
        assertTrue(status == HttpResponseStatus.OK || status == HttpResponseStatus.CREATED);

        // Create data change notification stream for a node in configuration datastore
        response = invokeRequest(HttpMethod.POST,
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

        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8), JSON_PARSER_CONFIGURATION);
        final var streamName = json.getJSONObject("sal-remote:output").getString("stream-name");
        assertNotNull(streamName, "Stream name is undefined");

        // get stream URL from restconf-state
        final var streamUrl = getStreamUrlJson(streamName);
        assertNotNull(streamUrl, "Stream URL not found");

    }
}
