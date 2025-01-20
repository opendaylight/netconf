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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Disabled until implementation of Notification Subscription is finished")
// FIXME change after NETCONF stream are properly setup and add response validation
public class NotificationSubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static final String JSON_ENCODING = "encode-json";
    private static final String NETCONF_STREAM = "NETCONF";
    private static final String MODIFY_SUBSCRIPTION_URI = "/restconf/operations/ietf-subscribed-notifications:"
        + "modify-subscription";
    private static final String DELETE_SUBSCRIPTION_URI = "/restconf/operations/ietf-subscribed-notifications:"
        + "delete-subscription";
    private static final String KILL_SUBSCRIPTION_URI = "/restconf/operations/ietf-subscribed-notifications:"
        + "kill-subscription";

    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    void establishSubscriptionTest() throws Exception {
        final var response = establishSubscription(NETCONF_STREAM, JSON_ENCODING);

        assertEquals(HttpResponseStatus.OK, response.status());

        assertNotNull(extractSubscriptionId(response));
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
                "subscription-id": "%s",
                "encoding": "encode-xml"
              }
            }""", subscriptionId);

        final var response = invokeRequest(HttpMethod.POST, MODIFY_SUBSCRIPTION_URI, APPLICATION_JSON, modifyInput);

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
        final var response = invokeRequest(HttpMethod.GET, "/restconf/subscriptions/" + subscriptionId,
            "text/event-stream");

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
                "subscription-id": "99999",
                "encoding": "%s"
              }
            }""", JSON_ENCODING);

        final var response = invokeRequest(HttpMethod.POST, MODIFY_SUBSCRIPTION_URI, APPLICATION_JSON, input);

        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
    }

    /**
     * Tests listening to a non-existent subscription.
     */
    @Test
    void listenToNonExistentSubscriptionTest() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, "/restconf/subscriptions/99999",
            "text/event-stream");

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
                "subscription-id": "%s"
              }
            }""", subscriptionId);

        final var response = invokeRequest(HttpMethod.POST, DELETE_SUBSCRIPTION_URI, APPLICATION_JSON, deleteInput);

        assertEquals(HttpResponseStatus.OK, response.status());
    }

    /**
     * Tests deleting a non-existent subscription.
     */
    @Test
    void deleteNonExistentSubscriptionTest() throws Exception {
        final var input = """
            {
              "input": {
                "subscription-id": "99999"
              }
            }""";

        final var response = invokeRequest(HttpMethod.POST, DELETE_SUBSCRIPTION_URI, APPLICATION_JSON, input);

        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
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
                "subscription-id": "%s"
              }
            }""", subscriptionId);

        final var response = invokeRequest(HttpMethod.POST, KILL_SUBSCRIPTION_URI, APPLICATION_JSON, killInput);

        assertEquals(HttpResponseStatus.OK, response.status());
    }

    /**
     * Tests killing a non-existent subscription.
     */
    @Test
    void killNonExistentSubscriptionTest() throws Exception {
        final var input = """
            {
              "input": {
                "subscription-id": "99999"
              }
            }""";

        final var response = invokeRequest(HttpMethod.POST, KILL_SUBSCRIPTION_URI, APPLICATION_JSON, input);

        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
    }
}
