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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opendaylight.restconf.it.ProtocolVersion;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class StreamsE2ETest extends AbstractE2ETest {
    @Override
    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();

        // init parent data
        final var response = invokeRequest(HttpMethod.PUT, "/rests/data/example-jukebox:jukebox",
            ProtocolVersion.HTTP_1_1, APPLICATION_JSON,
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
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void dataChangeEventStreamJsonTest(final ProtocolVersion version) throws Exception {
        // Create data change notification stream for a node in configuration datastore
        final var streamUrl = createNotificationStream(version);

        // start stream
        final var streamClient = startStreamClient(version);
        try {
            final var eventListener = startStream(streamUrl.getPath());

            // update datastore using other client to trigger notification events
            var response = invokeRequest(HttpMethod.POST,
                "/rests/data/example-jukebox:jukebox/library/artist=artist",
                version, APPLICATION_JSON, """
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
                version, APPLICATION_JSON, """
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
                "/rests/data/example-jukebox:jukebox/library/artist=artist/album=album", version);
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
            closeAllStreams();
            await().atMost(Duration.ofSeconds(1)).until(eventListener::ended);
        } finally {
            streamClient.shutdown().get(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Tests listening on multiple streams at same time with one client.
     *
     * <p>NB: not parametrized over HTTP_1_1 -- listening on multiple concurrent streams over a single client
     * connection requires multiplexing, which a plain HTTP/1.1 connection does not provide (it carries a single
     * in-flight request at a time), unlike HTTP_2 and HTTP_3.
     */
    @ParameterizedTest
    @EnumSource(value = ProtocolVersion.class, names = {"HTTP_2", "HTTP_3"})
    void listenMultipleStreams(final ProtocolVersion version) throws Exception {
        // Create first stream
        final var stream1 = createNotificationStream(version);
        // Create second stream
        final var stream2 = createNotificationStream(version);

        // start stream
        final var streamClient = startStreamClient(version);
        try {
            final var eventListener1 = startStream(stream1.getPath());
            final var eventListener2 = startStream(stream2.getPath());

            // update datastore to trigger notification events
            final var response = invokeRequest(HttpMethod.POST,
                "/rests/data/example-jukebox:jukebox/library/artist=artist",
                version, APPLICATION_JSON, """
                    {
                        "example-jukebox:album": [{
                            "name": "album",
                            "genre": "example-jukebox:rock",
                            "year": 2020
                        }]
                    }""");

            // confirm both listeners received notification
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
            closeAllStreams();
            await().atMost(Duration.ofSeconds(1)).until(eventListener1::ended);
            await().atMost(Duration.ofSeconds(1)).until(eventListener2::ended);
        } finally {
            streamClient.shutdown().get(5, TimeUnit.SECONDS);
        }
    }

    private URI createNotificationStream(final ProtocolVersion version) throws Exception {
        final var response = invokeRequest(HttpMethod.POST,
            "/rests/operations/sal-remote:create-data-change-event-subscription",
            version, APPLICATION_JSON,
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
        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8), jsonParserConfiguration());
        final var streamName = json.getJSONObject("sal-remote:output").getString("stream-name");
        assertNotNull(streamName, "Stream name is undefined");

        // get stream URL from restconf-state
        final var streamUrl = getStreamUrlJson(streamName, version);
        assertNotNull(streamUrl, "Stream URL not found");
        return streamUrl;
    }
}
