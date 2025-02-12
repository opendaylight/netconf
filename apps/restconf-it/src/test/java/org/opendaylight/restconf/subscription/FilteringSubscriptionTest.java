/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterOutOfBread;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class FilteringSubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static final NodeIdentifier BREAD_NODEID =
        NodeIdentifier.create(QName.create(ToasterRestocked.QNAME, "amountOfBread").intern());
    private static final Instant EVENT_TIME =
        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();

    private static HTTPClient streamClient;

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

    /**
     * Tests successful establish subscription RPC.
     */
    @Test
    void filterNotificationReceivedTest() throws Exception {
        streamClient = startStreamClient();
        final var response = establishFilteredSubscription(NETCONF_STREAM, ENCODE_XML, """
            <toasterRestocked xmlns="http://netconfcentral.org/ns/toaster">
              <amountOfBread/>
            </toasterRestocked>
            """);

        assertEquals(HttpResponseStatus.OK, response.status());

        startStreamClient();
        final var eventListener = startSubscriptionStream("2147483648");

        final var toasterRestockedNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(BREAD_NODEID, 1))
            .build();
        publishService.putNotification(new DOMNotificationEvent.Rfc6020(toasterRestockedNotification, EVENT_TIME));

        // verify ToasterRestocked notification is received
        JSONAssert.assertEquals(String.format("""
                {
                  "ietf-restconf:notification": {
                    "event-time": "%s",
                    "toaster:toasterRestocked": {
                      "amountOfBread": 1
                    }
                  }
                }""", EVENT_TIME.atOffset(ZoneOffset.ofHours(1))), eventListener.readNext(), JSONCompareMode.LENIENT);

        final var modifyInput = """
            {
              "input": {
                "id": 2147483648,
                "stop-time": "2025-03-20T15:30:00Z"
              }
            }""";
        final var modifyResponse = invokeRequestKeepClient(streamClient, HttpMethod.POST, MODIFY_SUBSCRIPTION_URI,
            APPLICATION_JSON, modifyInput, APPLICATION_JSON);
        assertEquals(HttpResponseStatus.NO_CONTENT, modifyResponse.status());

        // verify subscription-modified notification is not filtered out
        JSONAssert.assertEquals("""
            {
                "ietf-restconf:notification": {
                    "ietf-subscribed-notifications:subscription-modified" : {
                        "stream" : "NETCONF",
                        "id" : 2147483648,
                        "stop-time" : "2025-03-20T15:30:00Z",
                        "encoding" : "ietf-subscribed-notifications:encode-xml"
                    }
                }
            }""", eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    @Disabled
    @Test
    void filterNotificationNotReceivedTest() throws Exception {
        streamClient = startStreamClient();
        final var response = establishFilteredSubscription(NETCONF_STREAM, ENCODE_XML,
            "<toasterOutOfBread xmlns=\"http://netconfcentral.org/ns/toaster\"/>");
        assertEquals(HttpResponseStatus.OK, response.status());

        startStreamClient();
        final var eventListener = startSubscriptionStream("2147483648");

        final var toasterRestockedNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
            .withChild(ImmutableNodes.leafNode(BREAD_NODEID, 1))
            .build();
        publishService.putNotification(new DOMNotificationEvent.Rfc6020(toasterRestockedNotification, EVENT_TIME));

        // verify notification is filtered out
        assertNull(eventListener.readNext());

        final var toasterOutOfBreadNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterOutOfBread.QNAME))
            .build();
        publishService.putNotification(new DOMNotificationEvent.Rfc6020(toasterOutOfBreadNotification, EVENT_TIME));

        // verify notification toasterOutOfBread is not filtered out
        JSONAssert.assertEquals(String.format("""
                {
                  "ietf-restconf:notification": {
                    "event-time": "%s",
                    "toaster:toasterOutOfBread": {}
                  }
                }""", EVENT_TIME.atOffset(ZoneOffset.ofHours(1))), eventListener.readNext(), JSONCompareMode.LENIENT);
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

        return invokeRequestKeepClient(streamClient, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:establish-subscription",
            APPLICATION_XML, input, APPLICATION_JSON);
    }
}
