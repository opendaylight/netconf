/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionTimeoutException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.it.subscription.AbstractNotificationSubscriptionTest;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class NotificationSubscriptionListeningHttp2Test extends AbstractNotificationSubscriptionTest {
    private static HTTPClient streamClient;

    @AfterEach
    @Override
    protected void afterEach() throws Exception {
        if (streamClient != null) {
            streamClient.shutdown().get(2, TimeUnit.SECONDS);
            streamClient = null;
        }
        super.afterEach();
    }

    /**
     * Tests sending and receiving custom notification.
     */
    @Test
    void testPutNotification() throws Exception {
        // create notification
        final var notificationNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(ToasterRestocked.QNAME, "amountOfBread"), 10))
            .build();

        // start event listener
        final var eventListener = startSubscriptionStream(startSubscription(), true);

        // send notification
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(notificationNode, Instant.now()));

        // assert notification is received
        JSONAssert.assertEquals("""
            {
              "ietf-restconf:notification" : {
                "toaster:toasterRestocked" : {
                  "amountOfBread" : 10
                }
              }
            }""", eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    /**
     * Tests receiving notification after deleting subscription.
     */
    @Test
    void testListenDeleteNotification() throws Exception {
        // start event listener
        final var subscriptionId = startSubscription();
        final var eventListener = startSubscriptionStream(subscriptionId, true);

        // Delete the subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:delete-subscription",
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "id": %s
                  }
                }
                """.formatted(subscriptionId), MediaTypes.APPLICATION_YANG_DATA_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals("""
        {
          "ietf-restconf:notification" : {
            "ietf-subscribed-notifications:subscription-terminated" : {
              "id" : 2147483648,
              "reason" : "ietf-subscribed-notifications:no-such-subscription"
            }
          }
        }""", eventListener.readNext(), JSONCompareMode.LENIENT);

        // Assert exception when try to listen to subscription after it should be terminated
        assertThrows(ConditionTimeoutException.class, () -> startSubscriptionStream("2147483648", true));
    }

    /**
     * Tests listening on multiple streams at same tame with one client.
     */
    @Test
    void listenMultipleStreams() throws Exception {
        // create subscriptions
        final var subscription1Id = startSubscription();
        final var subscription2Id = startSubscription();

        // create listeners on same client
        final var eventListener1 = startSubscriptionStream(subscription1Id, true);
        final var eventListener2 = startSubscriptionStreamOnExistingClient(subscription2Id);

        // create notification
        final var notificationNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(ToasterRestocked.QNAME, "amountOfBread"), 5))
            .build();

        // send notification
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(notificationNode, Instant.now()));

        // assert notification was received on both listeners
        JSONAssert.assertEquals("""
            {
              "ietf-restconf:notification" : {
                "toaster:toasterRestocked" : {
                  "amountOfBread" : 5
                }
              }
            }""", eventListener1.readNext(), JSONCompareMode.LENIENT);
        JSONAssert.assertEquals("""
            {
              "ietf-restconf:notification" : {
                "toaster:toasterRestocked" : {
                  "amountOfBread" : 5
                }
              }
            }""", eventListener2.readNext(), JSONCompareMode.LENIENT);
    }

    private String startSubscription() throws Exception {
        if (streamClient == null) {
            streamClient = startStreamClient();
        }
        final var uri = "/restconf/operations/ietf-subscribed-notifications:establish-subscription";
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, uri,
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());

        // Extract subscription ID from response
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8),
            JSON_PARSER_CONFIGURATION);
        return String.valueOf(jsonContent.getJSONObject("ietf-subscribed-notifications:output").getLong("id"));
    }
}
