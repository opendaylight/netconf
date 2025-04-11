/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class StreamsE2ETest extends AbstractE2ETest {
    @Override
    @AfterEach
    void afterEach() throws Exception {
        if (clientStreamService != null) {
            clientStreamService = null;
        }
        if (streamControl != null) {
            streamControl = null;
        }
        super.afterEach();
    }

    @Disabled
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
        // {
        //      "sal-remote:output": {
        //          "stream-name":"urn:uuid:6413c077-5dfe-464c-b17f-20c5bbb456f4"
        //       }
        // }
        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        final var streamName = json.getJSONObject("sal-remote:output").getString("stream-name");
        assertNotNull(streamName, "Stream name is undefined");

        // get stream URL from restconf-state
        final var streamUrl = getStreamUrlJson(streamName);
        assertNotNull(streamUrl, "Stream URL not found");

        // start stream
        final var streamClient = startStreamClient();
        try {
            final var eventListener = startStream(streamUrl.getPath());

            // update datastore using other client to trigger notification events
            response = invokeRequest(HttpMethod.POST,
                "/rests/data/example-jukebox:jukebox/library/artist=artist",
                APPLICATION_JSON, """
                    {
                        "example-jukebox:album": [{
                            "name": "album",
                            "genre": "example-jukebox:rock",
                            "year": 2020
                        }]
                    }""");
            assertEquals(HttpResponseStatus.CREATED, response.status());
            JSONAssert.assertEquals("""
                {
                    "ietf-restconf:notification": {
                        "sal-remote:data-changed-notification": {
                            "data-change-event": [{
                                "path": "/example-jukebox:jukebox/library/artist[name='artist']/album[name='album']",
                                "operation": "created",
                                "data": {
                                    "example-jukebox:album": [{
                                        "name": "album",
                                        "genre": "example-jukebox:rock",
                                        "year": 2020
                                    }]
                                }
                            }]
                        }
                    }
                }""", eventListener.readNext(), JSONCompareMode.LENIENT);

            response = invokeRequest(HttpMethod.PUT,
                "/rests/data/example-jukebox:jukebox/library/artist=artist/album=album",
                APPLICATION_JSON, """
                 {
                    "example-jukebox:album": [{
                        "name": "album",
                        "year": 2024
                    }]
                }""");
            assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
            JSONAssert.assertEquals("""
                {
                    "ietf-restconf:notification": {
                        "sal-remote:data-changed-notification": {
                            "data-change-event": [{
                                "path": "/example-jukebox:jukebox/library/artist[name='artist']/album[name='album']",
                                "operation": "updated",
                                "data": {
                                    "example-jukebox:album": [{
                                        "name": "album",
                                        "year": 2024
                                    }]
                                }
                            }]
                        }
                    }
                }""", eventListener.readNext(), JSONCompareMode.LENIENT);

            response = invokeRequest(HttpMethod.DELETE,
                "/rests/data/example-jukebox:jukebox/library/artist=artist/album=album");
            assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
            JSONAssert.assertEquals("""
                {
                    "ietf-restconf:notification": {
                        "sal-remote:data-changed-notification": {
                            "data-change-event": [{
                                "path": "/example-jukebox:jukebox/library/artist[name='artist']/album[name='album']",
                                "operation": "deleted"
                            }]
                        }
                    }
                }""", eventListener.readNext(), JSONCompareMode.LENIENT);

            // terminate stream
            streamControl.close();
            await().atMost(Duration.ofSeconds(1)).until(eventListener::ended);

        } finally {
            streamClient.shutdown().get(2, TimeUnit.SECONDS);
        }
    }
}
