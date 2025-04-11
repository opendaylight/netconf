/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.it.server.TestEventStreamListener;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class NotificationSubscriptionListeningTest extends AbstractNotificationSubscriptionTest {
    private static final String TERMINATED_NOTIFICATION = """
        {
          "ietf-restconf:notification" : {
            "ietf-subscribed-notifications:subscription-terminated" : {
              "id" : 2147483648,
              "reason" : "ietf-subscribed-notifications:no-such-subscription"
            }
          }
        }""";

    private static HTTPClient streamClient;
    private static TestEventStreamListener eventListener;

    @BeforeEach
    public void beforeEach() throws Exception {
        super.beforeEach();
        streamClient = startStreamClient();

        // Establish subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
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
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        final var subscriptionId = jsonContent.getJSONObject("ietf-subscribed-notifications:output").getLong("id");

        // Start listening on notifications
        eventListener = startSubscriptionStream(String.valueOf(subscriptionId));
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
     * Tests sending and receiving custom notification.
     */
    @Test
    void testPutNotification() throws Exception {
        //create notification
        final var notificationNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(ToasterRestocked.QNAME, "amountOfBread"), 10))
            .build();

        //send notification
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(notificationNode, Instant.now()));

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
     * Tests receiving subscription modified notification.
     */
    @Disabled("Disabled until filtering is implemented in NETCONF-1436 and modifySubscription works")
    @Test
    void testListenModifiedNotification() throws Exception {
        // Modify the subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:modify-subscription",
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "id": 2147483648,
                    "stream-subtree-filter": {
                      "users" : {}
                     },
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals("""
            {
                "ietf-restconf:notification": {
                    "ietf-subscribed-notifications:subscription-modified" : {
                        "stream" : "NETCONF",
                        "id" : 2147483648,
                        "stream-subtree-filter": {
                            "users" : {}
                        },
                        "encoding" : "ietf-subscribed-notifications:encode-json"
                    }
                }
            }""", eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    /**
     * Tests receiving notification after deleting subscription.
     */
    @Test
    void testListenDeleteNotification() throws Exception {
        // Delete the subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:delete-subscription",
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "id": 2147483648
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals(TERMINATED_NOTIFICATION, eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    /**
     * Tests receiving notification after killing subscription.
     */
    @Disabled("Disabled until KillSubscriptionRpc is enabled after NETCONF-1353 is resolved")
    @Test
    void testListenKillNotification() throws Exception {
        // Kill the subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:kill-subscription",
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "id": 2147483648
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals(TERMINATED_NOTIFICATION, eventListener.readNext(), JSONCompareMode.LENIENT);
    }
}
