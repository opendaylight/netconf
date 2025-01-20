/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class NotificationSubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static final String APPLICATION_JSON = "application/json";
    private static final String JSON_ENCODING = "encode-json";
    private static final String NETCONF_STREAM = "NETCONF";
    private static final String MODIFY_SUBSCRIPTION_URI =
        "/restconf/operations/ietf-subscribed-notifications:modify-subscription";
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
        final var response = establishSubscription(NETCONF_STREAM, JSON_ENCODING);

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
        final var establishResponse = establishSubscription(NETCONF_STREAM, JSON_ENCODING);
        assertEquals(HttpResponseStatus.OK, establishResponse.status());

        final var subscriptionId = extractSubscriptionId(establishResponse);

        // Modify the subscription
        final var modifyInput = String.format("""
            {
              "input": {
                "id": %s,
                "stop-time": "2025-03-20T15:30:00Z"
              }
            }""", subscriptionId);

        final var response = invokeRequest(HttpMethod.POST, MODIFY_SUBSCRIPTION_URI, APPLICATION_JSON,
            modifyInput, APPLICATION_JSON);

        assertEquals(HttpResponseStatus.OK, response.status());
    }

    /**
     * Tests listening to notifications.
     */
    @Test
    void listenToNotificationsTest() throws Exception {
        final var establishResponse = establishSubscription(NETCONF_STREAM, JSON_ENCODING);
        assertEquals(HttpResponseStatus.OK, establishResponse.status());

        final var subscriptionId = extractSubscriptionId(establishResponse);

        // Listen to notifications
        final var response = invokeRequest(HttpMethod.GET, "/subscriptions/" + subscriptionId, "text/event-stream");

        assertEquals(HttpResponseStatus.OK, response.status());
    }

    /**
     * Tests invalid input for establishing subscription RPC.
     */
    @Test
    void establishSubscriptionInvalidInputTest() throws Exception {
        final var response = establishSubscription("unknown-stream", JSON_ENCODING);
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
    }

    /**
     * Tests modifying a non-existent subscription.
     */
    @Test
    void modifyNonExistentSubscriptionTest() throws Exception {
        final var input = String.format("""
            {
              "input": {
                "id": 99999,
                "stop-time": "2025-03-20T15:30:00Z"
              }
            }""", JSON_ENCODING);

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
        final var establishResponse = establishSubscription(NETCONF_STREAM, JSON_ENCODING);
        assertEquals(HttpResponseStatus.OK, establishResponse.status());

        final var subscriptionId = extractSubscriptionId(establishResponse);

        // Delete the subscription
        final var deleteInput = String.format("""
            {
              "input": {
                "id": %s
              }
            }""", subscriptionId);

        final var response = invokeRequest(HttpMethod.POST, DELETE_SUBSCRIPTION_URI, APPLICATION_JSON, deleteInput,
            APPLICATION_JSON);

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
        final var establishResponse = establishSubscription(NETCONF_STREAM, JSON_ENCODING);
        assertEquals(HttpResponseStatus.OK, establishResponse.status());

        final var subscriptionId = extractSubscriptionId(establishResponse);
        // Kill the subscription
        final var killInput = String.format("""
            {
              "input": {
                "id": %s
              }
            }""", subscriptionId);

        final var response = invokeRequest(HttpMethod.POST, KILL_SUBSCRIPTION_URI, APPLICATION_JSON, killInput,
            APPLICATION_JSON);

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
     * Utility method to extract subscription ID from response.
     */
    private static String extractSubscriptionId(final FullHttpResponse response) throws JsonProcessingException {
        final var responseBody = response.content().toString(StandardCharsets.UTF_8);
        final var jsonNode = OBJECT_MAPPER.readTree(responseBody);
        return jsonNode.at("/ietf-subscribed-notifications:output/id").asText();
    }

    /**
     * Utility method to establish a subscription.
     */
    private FullHttpResponse establishSubscription(final String stream, final String encoding) throws Exception {
        final var input = String.format("""
            {
              "input": {
                "stream": "%s"
              }
            }""", stream, encoding);

        return invokeRequest(HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:establish-subscription",
            APPLICATION_JSON, input, APPLICATION_JSON);
    }
}
