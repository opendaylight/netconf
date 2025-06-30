/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        final var stopTime = Instant.now().plus(Duration.ofSeconds(2));
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
        assertEquals(HttpResponseStatus.OK, response.status());

        // Extract subscription ID from response
        final var subscriptionId = extractSubscriptionId(response);

        // Start listening on notifications
        final var eventListener = startSubscriptionStream(String.valueOf(subscriptionId));

        final var notification = Awaitility.await().atMost(2, TimeUnit.SECONDS).until(eventListener::readNext,
            Objects::nonNull);

        JSONAssert.assertEquals("""
            {
              "ietf-restconf:notification": {
                "ietf-subscribed-notifications:subscription-terminated": {
                  "id": 2147483648,
                  "reason" : "ietf-subscribed-notifications:no-such-subscription"
                }
              }
            }
            """, notification, JSONCompareMode.LENIENT);

        // Assert exception when try to listen to subscription after it should be terminated
        assertThrows(ConditionTimeoutException.class, () -> startSubscriptionStream(String.valueOf(subscriptionId)));
    }
}
