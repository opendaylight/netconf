/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.InsufficientResources;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class SubscriptionSuspensionTest extends AbstractNotificationSubscriptionTest {
    private static final NodeIdentifier BREAD_NODE_ID =
        NodeIdentifier.create(QName.create(ToasterRestocked.QNAME, "amountOfBread").intern());
    private static final ContainerNode TOASTER_RESTOCKED_NOTIFICATION = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
        .withChild(ImmutableNodes.leafNode(BREAD_NODE_ID, 1))
        .build();
    private static final Instant EVENT_TIME =
        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();
    private static final String FORMATTED_EVENT_TIME = EVENT_TIME.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    private HTTPClient streamClient;

    @BeforeEach
    void beforeEach() throws Exception {
        super.beforeEach();
        streamClient = startStreamClient();
    }

    @AfterEach
    @Override
    void afterEach() throws Exception {
        if (streamClient != null) {
            streamClient.shutdown().get(2, TimeUnit.SECONDS);
        }
        super.afterEach();
    }

    @Test
    void testSubscriptionSuspension() throws Exception {
        // Establish subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_JSON, """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());

        final var subscriptionId = Uint32.valueOf(extractSubscriptionId(response));
        final var listener = startSubscriptionStream(String.valueOf(subscriptionId));

        final var subscription = getStreamRegistry().lookupSubscription(subscriptionId);
        assertNotNull(subscription);

        // try to publish ToasterRestocked notification
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(TOASTER_RESTOCKED_NOTIFICATION, EVENT_TIME));
        // verify ToasterRestocked notification is received
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification": {
                "event-time": "%s",
                "toaster:toasterRestocked": {
                  "amountOfBread": 1
                }
              }
            }""", FORMATTED_EVENT_TIME), listener.readNext(), JSONCompareMode.STRICT);

        // suspend subscription
        subscription.suspendSubscription(InsufficientResources.QNAME);
        // verify subscription-suspended notification is received
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification" : {
                "ietf-subscribed-notifications:subscription-suspended" : {
                  "id" : %s,
                  "reason" : "ietf-subscribed-notifications:insufficient-resources",
                }
              }
            }""", subscriptionId), listener.readNext(), JSONCompareMode.LENIENT);

        // try to publish ToasterRestocked notification
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(TOASTER_RESTOCKED_NOTIFICATION, EVENT_TIME));
        // verify ToasterRestocked notification is not received
        assertNull(listener.readNext());

        // resume subscription by invoking modify subscription RPC
        final var modifyInput = String.format("""
            <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
              <id>%s</id>
              <stream-subtree-filter><toasterRestocked xmlns="http://netconfcentral.org/ns/toaster"/></stream-subtree-filter>
            </input>""", subscriptionId);
        final var modifyResponse = invokeRequestKeepClient(streamClient, HttpMethod.POST, MODIFY_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_XML, modifyInput, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.NO_CONTENT, modifyResponse.status());

        // consume subscription modified notification
        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(listener::readNext, Objects::nonNull);

        // verify subscription-resumed notification is received
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification" : {
                "ietf-subscribed-notifications:subscription-resumed" : {
                  "id" : %s,
                }
              }
            }""", subscriptionId), listener.readNext(), JSONCompareMode.LENIENT);

        // try to publish ToasterRestocked notification
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(TOASTER_RESTOCKED_NOTIFICATION, EVENT_TIME));
        // verify ToasterRestocked notification is received
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification": {
                "event-time": "%s",
                "toaster:toasterRestocked": {
                  "amountOfBread": 1
                }
              }
            }""", FORMATTED_EVENT_TIME), listener.readNext(), JSONCompareMode.STRICT);
    }

    @Test
    void testSuspendSuspendedSubscription() throws Exception {
        // Establish subscription
        final var response = invokeRequestKeepClient(streamClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_JSON, """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());

        final var subscriptionId = Uint32.valueOf(extractSubscriptionId(response));
        final var listener = startSubscriptionStream(String.valueOf(subscriptionId));

        final var subscription = getStreamRegistry().lookupSubscription(subscriptionId);
        assertNotNull(subscription);

        // suspend subscription
        subscription.suspendSubscription(InsufficientResources.QNAME);
        // verify subscription-suspended notification is received
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification" : {
                "ietf-subscribed-notifications:subscription-suspended" : {
                  "id" : %s,
                  "reason" : "ietf-subscribed-notifications:insufficient-resources",
                }
              }
            }""", subscriptionId), listener.readNext(), JSONCompareMode.LENIENT);

        //try to  suspend subscription that is already suspended
        subscription.suspendSubscription(InsufficientResources.QNAME);
        // verify subscription-suspended notification is NOT received
        assertNull(listener.readNext());
    }
}
