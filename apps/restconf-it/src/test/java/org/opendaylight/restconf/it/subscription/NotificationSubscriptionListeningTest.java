/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.awaitility.core.ConditionTimeoutException;
import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.it.ProtocolVersion;
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

    /**
     * Tests sending and receiving custom notification.
     */
    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void testPutNotification(final ProtocolVersion version) throws Exception {
        // create notification
        final var notificationNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(ToasterRestocked.QNAME, "amountOfBread"), 10))
            .build();

        // start event listener
        final var eventListener = startSubscriptionStream(startSubscription(), version);

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
     * Tests receiving subscription modified notification.
     */
    @Disabled("Will be disabled until NETCONF-1466 has been resolved")
    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void testListenModifiedNotification(final ProtocolVersion version) throws Exception {
        final var subscriptionId = startSubscription();
        final var eventListener = startSubscriptionStream(subscriptionId, version);

        // Modify the subscription
        final var response = invokeRequestKeepClient(HttpMethod.POST,
            "/rests/operations/ietf-subscribed-notifications:modify-subscription",
            MediaTypes.APPLICATION_YANG_DATA_XML, MediaTypes.APPLICATION_YANG_DATA_JSON, """
             <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <id>%s</id>
               <stream-subtree-filter><toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/></stream-subtree-filter>
             </input>""".formatted(subscriptionId));

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals("""
            {
                "ietf-restconf:notification": {
                    "ietf-subscribed-notifications:subscription-modified" : {
                        "stream" : "NETCONF",
                        "id" : %s,
                        "stream-subtree-filter": {
                            "users" : {}
                        },
                        "encoding" : "ietf-subscribed-notifications:encode-json"
                    }
                }
            }""".formatted(subscriptionId), eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    /**
     * Tests receiving notification after deleting subscription.
     */
    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void testListenDeleteNotification(final ProtocolVersion version) throws Exception {
        // start event listener
        final var subscriptionId = startSubscription();
        final var eventListener = startSubscriptionStream(subscriptionId, version);

        // Delete the subscription
        final var response = invokeRequestKeepClient(HttpMethod.POST,
            "/rests/operations/ietf-subscribed-notifications:delete-subscription",
            MediaTypes.APPLICATION_YANG_DATA_JSON, MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "id": %s
                  }
                }
                """.formatted(subscriptionId));

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals(TERMINATED_NOTIFICATION, eventListener.readNext(), JSONCompareMode.LENIENT);

        // Assert exception when try to listen to subscription after it should be terminated
        assertThrows(ConditionTimeoutException.class, () -> startSubscriptionStream(subscriptionId, version));
        // Verify notification listening ended
        await().atMost(Duration.ofSeconds(5)).until(eventListener::ended);
        assertTrue(eventListener.ended());
    }

    /**
     * Tests receiving notification after killing subscription.
     */
    @Disabled("Disabled until KillSubscriptionRpc is enabled after NETCONF-1353 is resolved")
    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void testListenKillNotification(final ProtocolVersion version) throws Exception {
        final var subscriptionId = startSubscription();
        final var eventListener = startSubscriptionStream(subscriptionId, version);

        // Kill the subscription
        final var response = invokeRequestKeepClient(HttpMethod.POST,
            "/rests/operations/ietf-subscribed-notifications:kill-subscription",
            MediaTypes.APPLICATION_YANG_DATA_JSON, MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "id": %s
                  }
                }
                """.formatted(subscriptionId));

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals(TERMINATED_NOTIFICATION, eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    /**
     * Tests listening on multiple streams at same time with one client.
     *
     * <p>NB: not parametrized over HTTP_1_1 -- listening on multiple concurrent streams over a single client
     * connection requires multiplexing, which a plain HTTP/1.1 connection does not provide (it carries a single
     * in-flight request at a time), unlike HTTP_2 and HTTP_3.
     */
    @ParameterizedTest
    @EnumSource(value = ProtocolVersion.class, names = {"HTTP_2", "HTTP_3"})
    void listenMultipleStreams(final ProtocolVersion version) throws Exception {
        // create subscriptions
        final var subscription1Id = startSubscription();
        final var subscription2Id = startSubscription();

        // create listeners on same client
        final var eventListener1 = startSubscriptionStream(subscription1Id, version);
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

    private String startSubscription() {
        final var uri = "/rests/operations/ietf-subscribed-notifications:establish-subscription";
        final var response = invokeRequestKeepClient(HttpMethod.POST, uri,
            MediaTypes.APPLICATION_YANG_DATA_JSON, MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""");
        assertEquals(HttpResponseStatus.OK, response.status());

        // Extract subscription ID from response
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8),
            jsonParserConfiguration());
        return String.valueOf(jsonContent.getJSONObject("ietf-subscribed-notifications:output").getLong("id"));
    }
}
