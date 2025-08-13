/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class StopTimeTest extends AbstractNotificationSubscriptionTest {
    private static HTTPClient streamClient;

    @BeforeEach
    void beforeEach() throws Exception {
        super.beforeEach();
        streamClient = startStreamClient();
    }

    @AfterEach
    @Override
    void afterEach() throws Exception {
        if (streamClient != null) {
            streamClient.shutdown().get(2, TimeUnit.SECONDS);
        }
        super.afterEach();
    }

    /**
     * Test establishing subscription with stop-time in the past.
     */
    @Test
    void invalidStopTimeTest() {
        final var stopTime = Instant.now().minus(Duration.ofDays(1));
        // Establish subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            String.format("""
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json",
                    "stop-time": "%s"
                  }
                }""", stopTime), MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
        JSONAssert.assertEquals("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "invalid-value",
                    "error-message": "Stop-time must be in future.",
                    "error-type": "application"
                  }
                ]
              }
            }
            """, response.content().toString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
    }

    /**
     * Tests that a subscription is correctly terminated when the stop-time is reached
     * and a subscription-terminated notification is received.
     */
    @Test
    void subscriptionStopTimeTerminationTest() throws Exception {
        final var subscriptionId = establishSubscription(Instant.now().plus(Duration.ofSeconds(2)));

        // Start listening on notifications
        final var eventListener = startSubscriptionStream(subscriptionId);

        final var notification = Awaitility.await().atMost(2, TimeUnit.SECONDS).until(eventListener::readNext,
            Objects::nonNull);

        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification": {
                "ietf-subscribed-notifications:subscription-terminated": {
                  "id": %s,
                  "reason" : "ietf-subscribed-notifications:no-such-subscription"
                }
              }
            }
            """, subscriptionId), notification, JSONCompareMode.LENIENT);

        // Assert exception when try to listen to subscription after it should be terminated
        assertThrows(ConditionTimeoutException.class, () -> startSubscriptionStream(subscriptionId));
    }

    /**
     * Tests that modifying a subscription's stop-time to an earlier value correctly updates its lifecycle.
     *
     * <p>Initially, the first subscription has a later stop-time than the second.
     * After modification, it should terminate before the second one.
     */
    @Test
    void subscriptionStopTimeModifiedTest() throws Exception {
        final var subscriptionId1 = establishSubscription(Instant.now().plus(Duration.ofHours(1)));
        final var subscriptionId2 = establishSubscription(Instant.now().plus(Duration.ofSeconds(8)));

        // Start listening on notifications
        final var eventListener1 = startSubscriptionStream(subscriptionId1);
        final var eventListener2 = startSubscriptionStream(subscriptionId2);

        // modify the first subscription to have earlier stop time than the second one
        final var newStopTime = Instant.now().plus(Duration.ofSeconds(2));
        final var modifyInput = String.format("""
             <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <id>%s</id>
               <stream-subtree-filter><toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/></stream-subtree-filter>
               <stop-time>%s</stop-time>
             </input>""", subscriptionId1, newStopTime);
        final var modifyResponse = invokeRequestKeepClient(streamClient, HttpMethod.POST, MODIFY_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_XML, modifyInput, MediaTypes.APPLICATION_YANG_DATA_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, modifyResponse.status());

        // receive subscription modified notification
        var notification1 = Awaitility.await().atMost(1, TimeUnit.SECONDS).until(eventListener1::readNext,
            Objects::nonNull);
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification" : {
                "ietf-subscribed-notifications:subscription-modified" : {
                  "id" : %s,
                  "stream" : "NETCONF",
                  "encoding" : "ietf-subscribed-notifications:encode-json",
                  "stop-time" : "%s"
                }
              }
            }""", subscriptionId1, newStopTime.toString()), notification1, JSONCompareMode.LENIENT);

        // receive subscription terminated notification for the first subscription
        notification1 = Awaitility.await().atMost(2, TimeUnit.SECONDS).until(eventListener1::readNext,
            Objects::nonNull);
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification": {
                "ietf-subscribed-notifications:subscription-terminated": {
                  "id": %s,
                  "reason" : "ietf-subscribed-notifications:no-such-subscription"
                }
              }
            }
            """, subscriptionId1), notification1, JSONCompareMode.LENIENT);

        // there should be no notification for a second subscription yet
        assertNull(eventListener2.readNext());

        // receive subscription terminated notification for a second subscription
        final var notification2 = Awaitility.await().atMost(8, TimeUnit.SECONDS).until(eventListener2::readNext,
            Objects::nonNull);
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification": {
                "ietf-subscribed-notifications:subscription-terminated": {
                  "id": %s,
                  "reason" : "ietf-subscribed-notifications:no-such-subscription"
                }
              }
            }
            """, subscriptionId2), notification2, JSONCompareMode.LENIENT);
    }

    /**
     * Tests modify subscription with absent stop-time.
     *
     * <p>Test that modifying subscription with absent field of stop-time doesn't affect stop-time and subscription is
     * properly terminated when time is reached.
     */
    @Test
    void subscriptionModifiedAbsentStopTimeTest() throws Exception {
        final var subscriptionId = establishSubscription(Instant.now().plus(Duration.ofSeconds(2)));

        // Start listening on notifications
        final var eventListener = startSubscriptionStream(subscriptionId);

        // modify the subscription with absent stop time
        final var modifyInput = String.format("""
             <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <id>%s</id>
               <stream-subtree-filter><toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/></stream-subtree-filter>
             </input>""", subscriptionId);
        final var modifyResponse = invokeRequestKeepClient(streamClient, HttpMethod.POST, MODIFY_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_XML, modifyInput, MediaTypes.APPLICATION_YANG_DATA_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, modifyResponse.status());

        // receive subscription modified notification
        var notification1 = Awaitility.await().atMost(1, TimeUnit.SECONDS).until(eventListener::readNext,
            Objects::nonNull);
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification" : {
                "ietf-subscribed-notifications:subscription-modified" : {
                  "id" : %s,
                  "stream" : "NETCONF",
                  "encoding" : "ietf-subscribed-notifications:encode-json",
                }
              }
            }""", subscriptionId), notification1, JSONCompareMode.LENIENT);

        // receive subscription terminated notification for a second subscription
        final var notification2 = Awaitility.await().atMost(3, TimeUnit.SECONDS).until(eventListener::readNext,
            Objects::nonNull);
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification": {
                "ietf-subscribed-notifications:subscription-terminated": {
                  "id": %s,
                  "reason" : "ietf-subscribed-notifications:no-such-subscription"
                }
              }
            }
            """, subscriptionId), notification2, JSONCompareMode.LENIENT);
    }

    private String establishSubscription(final Instant stopTime) {
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            String.format("""
            {
              "input": {
                "stream": "NETCONF",
                "encoding": "encode-json",
                "stop-time": "%s"
              }
            }""", stopTime), MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());
        return String.valueOf(extractSubscriptionId(response));
    }
}
