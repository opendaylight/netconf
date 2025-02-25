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
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class FilteringSubscriptionTest extends AbstractNotificationSubscriptionTest {
    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    void filterNotificationTest() throws Exception {
        final var response = establishFilteredSubscription(NETCONF_STREAM, ENCODE_XML, """
            <toasterRestocked xmlns="http://netconfcentral.org/ns/toaster">
              <amountOfBread/>
            </toasterRestocked>
            """);

        assertEquals(HttpResponseStatus.OK, response.status());
        //assertNotNull(extractSubscriptionId(response));

        //final var subscriptionId = extractSubscriptionId(response);
        final var streamClient = startStreamClient();
        final var eventListener = startSubscriptionStream("2147483648");

        // invoke RPC
        JSONAssert.assertEquals("""
                data: {
                  "ietf-restconf:notification": {
                    "event-time":"2025-02-18T12:55:12.888818418+01:00",
                    "toaster:toasterRestocked": {
                      "amountOfBread":1
                    }
                  }
                }""", eventListener.readNext(), JSONCompareMode.LENIENT);
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
}
