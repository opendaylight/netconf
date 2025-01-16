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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class NotificationSubscriptionTest extends AbstractNotificationSubscriptionTest {
    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    void establishSubscriptionTest() throws Exception {
        final var input = """
            {
              "input": {
                "stream": "NETCONF"
              }
            }""";

        final var response = invokeRequest(HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:establish-subscription",
            APPLICATION_JSON, input);

        assertEquals(HttpResponseStatus.OK, response.status());

        final var content = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals("""
            {
                "ietf-subscribed-notifications:output":{
                    "id":2147483648
                }
            }""", content, JSONCompareMode.LENIENT);
    }
}
