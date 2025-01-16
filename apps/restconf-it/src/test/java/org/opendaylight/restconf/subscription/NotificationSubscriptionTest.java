/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.handler.codec.http.HttpMethod;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class NotificationSubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static final String APPLICATION_JSON = "application/json";

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
}
