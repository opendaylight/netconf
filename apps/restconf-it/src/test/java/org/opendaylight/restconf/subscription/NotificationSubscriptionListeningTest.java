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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.server.netty.TestEventStreamListener;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class NotificationSubscriptionListeningTest extends AbstractNotificationSubscriptionTest {
    private static HTTPClient streamClient;
    private static TestEventStreamListener eventListener;

    @BeforeEach
    public void beforeEach() throws Exception {
        super.beforeEach();
        streamClient = startStreamClient();

        // Establish subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:establish-subscription",
            "application/json",
            """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""",
            "application/json");
        assertEquals(HttpResponseStatus.OK, response.status());

        // Get subscription ID from response
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        final Long subscriptionId = jsonContent.getJSONObject("ietf-subscribed-notifications:output").getLong("id");
        assertNotNull(subscriptionId, "Subscription ID is undefined");

        // Start listening on notifications
        eventListener = startSubscriptionStream(subscriptionId.toString());
    }

    @AfterEach
    @Override
    void afterEach() throws Exception {
        if (streamClient != null) {
            streamClient.shutdown().get(2, TimeUnit.SECONDS);
        }
        if (streamControl != null) {
            streamControl = null;
        }
        super.afterEach();
    }

    @Test
    void testPutNotification() throws Exception {
        //create notification
        var notificationNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(
                ToasterRestocked.QNAME, "amountOfBread"), 10))
            .build();

        //send notification
        publishService.putNotification(new DOMNotificationEvent.Rfc6020(notificationNode,
            Instant.ofEpochSecond(1738413500)));

        JSONAssert.assertEquals("""
            {
              "ietf-restconf:notification" : {
                "event-time" : "2025-02-01T13:38:20+01:00",
                "toaster:toasterRestocked" : {
                  "amountOfBread" : 10
                }
              }
            }""", eventListener.readNext(), JSONCompareMode.LENIENT);
    }
}
