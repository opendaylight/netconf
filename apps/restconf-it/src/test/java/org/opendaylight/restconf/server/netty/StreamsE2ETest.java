/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class StreamsE2ETest extends AbstractE2ETest {
    private volatile EventStreamService clientStreamService;
    private volatile EventStreamService.StreamControl streamControl;

    @AfterEach
    @Override
    void afterEach() {
        clientStreamService = null;
        streamControl = null;
        super.afterEach();
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void createStreamTest() throws Exception {
        // init data == topology node
        var response = invokeRequest(HttpMethod.PUT,
            "/rests/data/network-topology:network-topology",
            APPLICATION_JSON,
            """
                {
                    "network-topology:network-topology": {
                        "topology": [
                            {
                                "topology-id": "test",
                                "node": [ ]
                            }
                        ]
                    }
                }""");
        final var status = response.status();
        assertTrue(status == HttpResponseStatus.OK || status == HttpResponseStatus.CREATED);

        // Create data change notification stream for topology node in configuration datastore
        response = invokeRequest(HttpMethod.POST,
            "/rests/operations/sal-remote:create-data-change-event-subscription",
            APPLICATION_JSON,
            """
                {
                    "input": {
                        "path": "/network-topology:network-topology/topology[topology-id='test']/node[node-id='test']",
                        "sal-remote-augment:datastore": "CONFIGURATION",
                        "sal-remote-augment:scope": "ONE"
                    }
                }
                """);
        assertEquals(HttpResponseStatus.OK, response.status());
        final var streamName = extractStreamNameJson(response.content().toString(StandardCharsets.UTF_8));
        assertNotNull(streamName, "Stream name is undefined");

        // get stream URL from restconf-state
        response = invokeRequest(HttpMethod.GET,
            "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + streamName);
        assertEquals(HttpResponseStatus.OK, response.status());
        final var streamUrl = extractStreamUrlJson(response.content().toString(StandardCharsets.UTF_8));
        assertNotNull(streamUrl, "Stream URL not found");

        // connect
        final var transportListener = new TestTransportListener(channel -> {
            clientStreamService = SseUtils.enableClientSse(channel);
        });
        final var streamClient = HTTPClient.connect(transportListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, false).get(2, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(2)).until(transportListener::initialized);
        assertNotNull(clientStreamService);

        try {
            // request stream
            final var eventListener = new TestEventListener();
            clientStreamService.startEventStream(streamUrl.getPath(), eventListener,
                new EventStreamService.StartCallback() {
                    @Override
                    public void onStreamStarted(final EventStreamService.StreamControl control) {
                        streamControl = control;
                    }

                    @Override
                    public void onStartFailure(final Exception cause) {
                        fail("Stream was not started", cause);
                    }
                });
            await().atMost(Duration.ofSeconds(2)).until(eventListener::started);
            assertNotNull(streamControl);

            // update datastore using other client to trigger notification events
            response = invokeRequest(HttpMethod.POST,
                "/rests/data/network-topology:network-topology/topology=test",
                APPLICATION_JSON, """
                        {
                        "network-topology:node": [{
                            "node-id": "test",
                            "netconf-node-topology:login-password-unencrypted": {
                                "username": "admin",
                                "password": "admin"
                            }
                        }]
                    }
                    """);
            assertEquals(HttpResponseStatus.CREATED, response.status());
            JSONAssert.assertEquals("""
                {
                    "ietf-restconf:notification": {
                        "sal-remote:data-changed-notification": {
                            "data-change-event": [{
                                "path": "/network-topology:network-topology/topology[topology-id='test']/node[node-id='test']",
                                "operation": "created",
                                "data": {
                                    "network-topology:node": [{
                                        "node-id":"test",
                                        "netconf-node-topology:login-password-unencrypted": {
                                            "username":"admin",
                                            "password":"admin"
                                        }
                                    }]
                                }
                            }]
                        }
                    }
                }""", eventListener.readNext(), JSONCompareMode.LENIENT);

            response = invokeRequest(HttpMethod.PUT,
                "/rests/data/network-topology:network-topology/topology=test/node=test",
                APPLICATION_JSON, """
                        {
                        "network-topology:node": [
                            {
                                "node-id": "test",
                                "netconf-node-topology:login-password-unencrypted": {
                                    "password": "updated"
                                }
                            }
                        ]
                    }
                    """);
            assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
            JSONAssert.assertEquals("""
                {
                    "ietf-restconf:notification": {
                        "sal-remote:data-changed-notification": {
                            "data-change-event": [{
                                "path": "/network-topology:network-topology/topology[topology-id='test']/node[node-id='test']",
                                "operation": "updated",
                                "data": {
                                    "network-topology:node": [{
                                        "node-id": "test",
                                        "netconf-node-topology:login-password-unencrypted": {
                                            "password":"updated"
                                        }
                                    }]
                                }
                            }]
                        }
                    }
                }""", eventListener.readNext(), JSONCompareMode.LENIENT);

            response = invokeRequest(HttpMethod.DELETE,
                "/rests/data/network-topology:network-topology/topology=test/node=test");
            assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
            JSONAssert.assertEquals("""
                {
                    "ietf-restconf:notification": {
                        "sal-remote:data-changed-notification": {
                            "data-change-event": [{
                                "path": "/network-topology:network-topology/topology[topology-id='test']/node[node-id='test']",
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

    private static String extractStreamNameJson(final String content) {
        // {
        //      "sal-remote:output": {
        //          "stream-name":"urn:uuid:6413c077-5dfe-464c-b17f-20c5bbb456f4"
        //       }
        // }
        final var json = new JSONObject(content);
        return json.getJSONObject("sal-remote:output").getString("stream-name");
    }

    private static URI extractStreamUrlJson(final String content) {
        // {
        //      "ietf-restconf-monitoring:stream": [{
        //              "name": "urn:uuid:6413c077-5dfe-464c-b17f-20c5bbb456f4",
        //              "access": [
        //                  { "encoding": "json", "location": "..." },
        //                  { "encoding": "xml", "location": "..."}
        //              ],
        //              "description": "..."
        //      }]
        // }
        final var json = new JSONObject(content);
        final var stream = json.getJSONArray("ietf-restconf-monitoring:stream").getJSONObject(0);
        for (var access : stream.getJSONArray("access")) {
            final var accessObj = (JSONObject) access;
            if ("json".equals(accessObj.getString("encoding"))) {
                return URI.create(accessObj.getString("location"));
            }
        }
        return null;
    }

    private static final class TestEventListener implements EventStreamListener {
        private volatile boolean started = false;
        private volatile boolean ended = false;
        private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(5);

        @Override
        public void onStreamStart() {
            started = true;
        }

        boolean started() {
            return started;
        }

        @Override
        public void onEventField(@NonNull String fieldName, @NonNull String fieldValue) {
            if ("data".equals(fieldName)) {
                queue.add(fieldValue);
            }
        }

        String readNext() throws InterruptedException {
            return queue.poll(5, TimeUnit.SECONDS);
        }

        @Override
        public void onStreamEnd() {
            ended = true;
        }

        boolean ended() {
            return ended;
        }
    }
}
