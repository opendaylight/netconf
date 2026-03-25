/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

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

    @Override
    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();

        // init parent data
        final var response = http2Client.send(HttpRequest.newBuilder()
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
    }

    // FIXME: NETCONF-1590, fix and enable the test
    @Disabled
    @Test
    void dataChangeEventStreamJsonTest() throws Exception {
        // Create data change notification stream
        final var streamUrl = createNotificationStream();
        // start stream
        final var streamClient = startStreamClient(true);
        try {
            final var eventListener = startStream(streamUrl.getPath());

            // update datastore to trigger notification events
            var response = http2Client.send(HttpRequest.newBuilder()
                .uri(createUri("/rests/data/example-jukebox:jukebox/library/artist=artist"))
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                        "example-jukebox:album": [{
                            "name": "album",
                            "genre": "example-jukebox:rock",
                            "year": 2020
                        }]
                    }"""))
                .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
                .build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(HttpResponseStatus.CREATED.code(), response.statusCode());
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


            response = http2Client.send(HttpRequest.newBuilder()
                .uri(createUri("/rests/data/example-jukebox:jukebox/library/artist=artist/album=album"))
                .PUT(HttpRequest.BodyPublishers.ofString("""
                     {
                        "example-jukebox:album": [{
                            "name": "album",
                            "year": 2024
                        }]
                    }"""))
                .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
                .build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(HttpResponseStatus.NO_CONTENT.code(), response.statusCode());
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

            response = http2Client.send(HttpRequest.newBuilder()
                .uri(createUri("/rests/data/example-jukebox:jukebox/library/artist=artist/album=album"))
                .DELETE()
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpResponseStatus.NO_CONTENT.code(), response.statusCode());
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

    /**
     * Tests listening on multiple streams at same time with one client.
     */
    // FIXME: NETCONF-1590, fix and enable the test
    @Disabled
    @Test
    void listenMultipleStreams() throws Exception {
        // Create first stream
        final var stream1 = createNotificationStream();
        // Create second stream
        final var stream2 = createNotificationStream();

        // start stream
        final var streamClient = startStreamClient(true);
        try {
            final var eventListener1 = startStream(stream1.getPath());
            final var eventListener2 = startStream(stream2.getPath());

            // update datastore to trigger notification events
            var response = http2Client.send(HttpRequest.newBuilder()
                .uri(createUri("/rests/data/example-jukebox:jukebox/library/artist=artist"))
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                        "example-jukebox:album": [{
                            "name": "album",
                            "genre": "example-jukebox:rock",
                            "year": 2020
                        }]
                    }"""))
                .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
                .build(), HttpResponse.BodyHandlers.ofString());

            // confirm both listeners received notification
            assertEquals(HttpResponseStatus.CREATED.code(), response.statusCode());
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
                }""", eventListener1.readNext(), JSONCompareMode.LENIENT);

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
                }""", eventListener2.readNext(), JSONCompareMode.LENIENT);

            // terminate stream
            streamControl.close();
            await().atMost(Duration.ofSeconds(1)).until(eventListener1::ended);
            await().atMost(Duration.ofSeconds(1)).until(eventListener2::ended);
        } finally {
            streamClient.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private URI createNotificationStream() throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
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
        return streamUrl;
    }
}
