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
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
    private static final String APPLICATION_JSON = "application/json";
    private static final String TERMINATED_RESPONSE = """
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
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:establish-subscription",
            APPLICATION_JSON,
            """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""", APPLICATION_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());

        // Extract subscription ID from response
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        final var subscriptionId = jsonContent
            .getJSONObject("ietf-subscribed-notifications:output")
            .getLong("id");

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
        var notificationNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(ToasterRestocked.QNAME, "amountOfBread"), 10))
            .build();

        //send notification
        getPublishService().putNotification(
            new DOMNotificationEvent.Rfc6020(notificationNode, Instant.parse("2025-02-01T12:38:20Z")));

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
            APPLICATION_JSON,
            """
                {
                  "input": {
                    "id": 2147483648,
                    "stop-time": "2025-03-20T15:30:00Z"
                  }
                }""", APPLICATION_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals("""
            {
                "ietf-restconf:notification": {
                    "ietf-subscribed-notifications:subscription-modified" : {
                        "stream" : "NETCONF",
                        "id" : 2147483648,
                        "stop-time" : "2025-03-20T15:30:00Z",
                        "encoding" : "ietf-subscribed-notifications:encode-json"
                    }
                }
            }""", eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    /**
     * Tests receiving notification after deleting subscription.
     */
    @Test
    @Disabled("Disabled until AbstractRestconfStreamRegistry.SubscriptionImpl.terminateImpl complete request")
    void testListenDeleteNotification() throws Exception {
        // Modify the subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:delete-subscription",
            APPLICATION_JSON,
            """
                {
                  "input": {
                    "id": 2147483648
                  }
                }""", APPLICATION_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals(TERMINATED_RESPONSE, eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    /**
     * Tests receiving notification after killing subscription.
     */
    @Test
    @Disabled("Disabled until KillSubscriptionRpc is enabled after NETCONF-1353 is resolved")
    void testListenKillNotification() throws Exception {
        // Modify the subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:kill-subscription",
            APPLICATION_JSON,
            """
                {
                  "input": {
                    "id": 2147483648
                  }
                }""", APPLICATION_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals(TERMINATED_RESPONSE, eventListener.readNext(), JSONCompareMode.LENIENT);
    }
}
