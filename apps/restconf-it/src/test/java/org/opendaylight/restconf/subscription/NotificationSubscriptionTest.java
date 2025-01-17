/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NotificationSubscriptionTest extends AbstractNotificationSubscriptionTest {
    // FIXME change after NETCONF stream are properly setup and add response validation
    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    @Disabled
    void establishRPCTest() throws Exception {
        final var input = """
            {
              "input": {
                "stream": "NETCONF"
              }
            }""";

        final var response = invokeRequest(HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:establish-subscription",
            APPLICATION_JSON, input);

        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
    }
}
