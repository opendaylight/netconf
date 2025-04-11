/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

@Disabled
class NotificationSubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static final String APPLICATION_JSON = "application/json";
    private static final String JSON_ENCODING = "encode-json";
    private static final String NETCONF_STREAM = "NETCONF";
    private static final String DELETE_SUBSCRIPTION_URI =
        "/restconf/operations/ietf-subscribed-notifications:delete-subscription";
    private static final String KILL_SUBSCRIPTION_URI =
        "/restconf/operations/ietf-subscribed-notifications:kill-subscription";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Tests default NETCONF stream availability.
     */
    @Test
    void defaultStreamAvailabilityTest() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, "/restconf/data/ietf-subscribed-notifications:streams",
            APPLICATION_JSON);
        assertNotNull(response);
        assertNotNull(response.content());
        final var content = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals("""
            {
              "ietf-subscribed-notifications:streams": {
                "stream": [
                  {
                    "name": "NETCONF",
                    "description": "Stream for subscription state change notifications"
                  }
                ]
              }
            }
            """, content, JSONCompareMode.LENIENT);
    }

    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    void establishSubscriptionTest() throws Exception {
        final var response = establishSubscription(NETCONF_STREAM);
        assertEquals(HttpResponseStatus.OK, response.status());
        final var content = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals("""
            {
              "ietf-subscribed-notifications:output":{
                "id": 2147483648
              }
            }""", content, JSONCompareMode.LENIENT);
    }

    /**
     * Tests successful modify subscription RPC.
     */
    @Test
    void modifySubscriptionTest() throws Exception {
        final var request1 = prepareEstablishRPCRequest();
        // Modify the subscription
        final var modifyInput = """
            {
              "input": {
                "id": 2147483648,
                "stop-time": "2025-03-20T15:30:00Z"
              }
            }""";
        final var request2 = buildRequest(HttpMethod.POST, MODIFY_SUBSCRIPTION_URI, APPLICATION_JSON, modifyInput,
            APPLICATION_JSON);
        final var response = invokeTwoRequests(request1, request2);
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
    }

    /**
     * Tests listening to notifications.
     */
    @Test
    void listenToNotificationsTest() throws Exception {
        final var request1 = prepareEstablishRPCRequest();
        // Listen to notifications
        final var request2 = buildRequest(HttpMethod.GET, "/subscriptions/2147483648", APPLICATION_JSON, null,
            "text/event-stream");
        final var response = invokeTwoRequests(request1, request2);
        // Listen to notifications
        assertEquals(HttpResponseStatus.OK, response.status());
    }

    /**
     * Tests invalid input for establishing subscription RPC.
     */
    @Test
    void establishSubscriptionInvalidInputTest() throws Exception {
        final var response = establishSubscription("unknown-stream");
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
    }

    /**
     * Tests modifying a non-existent subscription.
     */
    @Test
    void modifyNonExistentSubscriptionTest() throws Exception {
        final var input = """
            {
              "input": {
                "id": 99999,
                "stop-time": "2025-03-20T15:30:00Z"
              }
            }""";
        final var response = invokeRequest(HttpMethod.POST, MODIFY_SUBSCRIPTION_URI, APPLICATION_JSON, input,
            APPLICATION_JSON);
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
    }

    /**
     * Tests listening to a non-existent subscription.
     */
    @Test
    void listenToNonExistentSubscriptionTest() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, "/subscriptions/99999", "text/event-stream");
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
    }

    /**
     * Tests deleting an existing subscription.
     */
    @Test
    void deleteSubscriptionTest() throws Exception {
        final var request1 = prepareEstablishRPCRequest();
        // Delete the subscription
        final var deleteInput = """
            {
              "input": {
                "id": 2147483648
              }
            }""";
        final var request2 = buildRequest(HttpMethod.POST, KILL_SUBSCRIPTION_URI, APPLICATION_JSON, deleteInput,
            APPLICATION_JSON);
        final var response = invokeTwoRequests(request1, request2);
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
    }

    /**
     * Tests deleting a non-existent subscription.
     */
    @Test
    void deleteNonExistentSubscriptionTest() throws Exception {
        final var input = """
            {
              "input": {
                "id": 99999
              }
            }""";
        final var response = invokeRequest(HttpMethod.POST, DELETE_SUBSCRIPTION_URI, APPLICATION_JSON, input,
            APPLICATION_JSON);
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
        final var jsonNode = OBJECT_MAPPER.readTree(response.content().toString(StandardCharsets.UTF_8));
        final var errorMessage = jsonNode.at("/errors/error/0/error-message").asText();
        assertEquals("No subscription with given ID.", errorMessage);
    }

    /**
     * Tests killing an existing subscription.
     */
    @Test
    void killSubscriptionTest() throws Exception {
        final var request1 = prepareEstablishRPCRequest();
        // Kill the subscription
        final var killInput = """
            {
              "input": {
                "id": 2147483648
              }
            }""";
        final var request2 = buildRequest(HttpMethod.POST, KILL_SUBSCRIPTION_URI, APPLICATION_JSON, killInput,
            APPLICATION_JSON);
        final var response = invokeTwoRequests(request1, request2);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
    }

    /**
     * Tests killing a non-existent subscription.
     */
    @Test
    void killNonExistentSubscriptionTest() throws Exception {
        final var input = """
            {
              "input": {
                "id": 99999
              }
            }""";
        final var response = invokeRequest(HttpMethod.POST, KILL_SUBSCRIPTION_URI, APPLICATION_JSON, input,
            APPLICATION_JSON);
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
        final var jsonNode = OBJECT_MAPPER.readTree(response.content().toString(StandardCharsets.UTF_8));
        final var errorMessage = jsonNode.at("/errors/error/0/error-message").asText();
        assertEquals("No subscription with given ID.", errorMessage);
    }

    /**
     * Utility method to establish a subscription.
     */
    private FullHttpResponse establishSubscription(final String stream) throws Exception {
        final var input = String.format("""
            {
              "input": {
                "stream": "%s",
                "encoding": "%s"
              }
            }""", stream, JSON_ENCODING);
        return invokeRequest(HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI, APPLICATION_JSON, input, APPLICATION_JSON);
    }

    private FullHttpRequest prepareEstablishRPCRequest() {
        final var input = String.format("""
            {
              "input": {
                "stream": "%s",
                "encoding": "%s"
              }
            }""", NETCONF_STREAM, JSON_ENCODING);
        return buildRequest(HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI, APPLICATION_JSON, input, APPLICATION_JSON);
    }
}
