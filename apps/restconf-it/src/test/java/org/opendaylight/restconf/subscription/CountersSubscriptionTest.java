/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class CountersSubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static final String ID = "2147483648";

    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    void counterNotificationTest() throws Exception {
        final var response = establishFilteredSubscription(NETCONF_STREAM, ENCODE_XML, """
            <toasterRestocked xmlns="http://netconfcentral.org/ns/toaster">
              <amountOfBread/>
            </toasterRestocked>
            """);

        assertEquals(HttpResponseStatus.OK, response.status());

        startStreamClient();
        startSubscriptionStream(ID);

        // invoke RPC

        // modify
        final var modifyInput = """
            {
              "input": {
                "id": 2147483648,
                "stop-time": "2025-03-20T15:30:00Z"
              }
            }""";
        final var response2 = invokeRequest(HttpMethod.POST, MODIFY_SUBSCRIPTION_URI, APPLICATION_JSON, modifyInput,
            APPLICATION_JSON);
        assertEquals(HttpResponseStatus.NO_CONTENT, response2.status());

        assertCounter(ID, "2", "0");
    }

    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    void excludedNotificationTest() throws Exception {
        final var response = establishFilteredSubscription(NETCONF_STREAM, ENCODE_XML,
            "<toasterOutOfBread xmlns=\"http://netconfcentral.org/ns/toaster\"/>");

        assertEquals(HttpResponseStatus.OK, response.status());

        startStreamClient();
        startSubscriptionStream(ID);

        // invoke RPC which should be excluded as we filter for toasterOutOfBread notifications

        // modify
        final var modifyInput = """
            {
              "input": {
                "id": 2147483648,
                "stop-time": "2025-03-20T15:30:00Z"
              }
            }""";
        final var response2 = invokeRequest(HttpMethod.POST, MODIFY_SUBSCRIPTION_URI, APPLICATION_JSON, modifyInput,
            APPLICATION_JSON);
        assertEquals(HttpResponseStatus.NO_CONTENT, response2.status());

        assertCounter(ID, "1", "1");
    }

    /**
     * Utility method to establish a subscription.
     */
    protected FullHttpResponse establishFilteredSubscription(final String stream, final String encoding,
            final String filter) throws Exception {
        final var input = String.format("""
             <establish-subscription xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <stream>%s</stream>
               <encoding>%s</encoding>
               <stream-subtree-filter>%s</stream-subtree-filter>
             </establish-subscription>
             """, stream, encoding, filter);

        return invokeRequest(HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:establish-subscription",
            APPLICATION_XML, input, APPLICATION_JSON);
    }

    private void assertCounter(final String id, final String sent, final String excluded) throws Exception {
        final var receiver =  invokeRequest(HttpMethod.GET,
            "restconf/data/ietf-subscribed-notifications:subscriptions/subscription=" + id + "/receivers",
            APPLICATION_JSON, null, APPLICATION_JSON);
        assertEquals(HttpResponseStatus.OK, receiver.status());
        final var json = new JSONObject(receiver.content().toString(StandardCharsets.UTF_8))
            .getJSONObject("ietf-subscribed-notifications:receivers")
            .getJSONObject("receiver");
        final var sentEvents = json.getString("sent-event-records");
        final var excludedEvents = json.getString("excluded-event-records");
        assertEquals(sent, sentEvents);
        assertEquals(excluded, excludedEvents);
    }
}
