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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterOutOfBread;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterRestocked;
import org.opendaylight.yang.gen.v1.test.notification.rev250303.ExampleNotification;
import org.opendaylight.yang.gen.v1.test.notification.rev250303.example.notification.Entry;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

//@Disabled("Disabled until filtering is implemented in NETCONF-1436")
class FilteringSubscriptionTest extends AbstractNotificationSubscriptionTest {
    private static final NodeIdentifier BREAD_NODEID =
        NodeIdentifier.create(QName.create(ToasterRestocked.QNAME, "amountOfBread").intern());
    private static final QName QNAME_PROPERTY_ID = QName.create(Entry.QNAME, "id");
    private static final QName QNAME_PROPERTY_NAME = QName.create(Entry.QNAME, "name");
    private static final Instant EVENT_TIME =
        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();
    private static final ContainerNode TOASTER_RESTOCKED_NOTIFICATION = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(NodeIdentifier.create(ToasterRestocked.QNAME))
        .withChild(ImmutableNodes.leafNode(BREAD_NODEID, 1))
        .build();

    private static HTTPClient streamClient;

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
    void filterNotificationReceivedTest() throws Exception {
        final var response = establishFilteredSubscription("""
            <toasterRestocked xmlns="http://netconfcentral.org/ns/toaster">
              <amountOfBread/>
            </toasterRestocked>
            """, streamClient);

        assertEquals(HttpResponseStatus.OK, response.status());
        final var id = extractSubscriptionId(response);
        final var eventListener = startSubscriptionStream(String.valueOf(id));

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
            }""", EVENT_TIME), eventListener.readNext(), JSONCompareMode.LENIENT);

        final var modifyInput = String.format("""
             <establish-subscription xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <id>%s</id>
               <stream-subtree-filter><toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/></stream-subtree-filter>
             </establish-subscription>""", id);
        final var modifyResponse = invokeRequestKeepClient(streamClient, HttpMethod.POST, MODIFY_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_XML, modifyInput, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.NO_CONTENT, modifyResponse.status());

        // verify subscription-modified notification is not filtered out
        JSONAssert.assertEquals(String.format("""
            {
                "ietf-restconf:notification": {
                    "ietf-subscribed-notifications:subscription-modified" : {
                        "stream" : "NETCONF",
                        "id" : %s,
                        "encoding" : "ietf-subscribed-notifications:encode-xml"
                    }
                }
            }""", id), eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    @Test
    void filterNotificationNotReceivedTest() throws Exception {
        final var response = establishFilteredSubscription(
            "<toasterOutOfBread xmlns=\"http://netconfcentral.org/ns/toaster\"/>", streamClient);

        assertEquals(HttpResponseStatus.OK, response.status());
        final var id = extractSubscriptionId(response);
        final var eventListener = startSubscriptionStream(String.valueOf(id));

        publishService().putNotification(new DOMNotificationEvent.Rfc6020(TOASTER_RESTOCKED_NOTIFICATION, EVENT_TIME));

        // verify notification is filtered out
        assertNull(eventListener.readNext());

        final var toasterOutOfBreadNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ToasterOutOfBread.QNAME))
            .build();
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(toasterOutOfBreadNotification, EVENT_TIME));

        // verify notification toasterOutOfBread is not filtered out
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification": {
                "event-time": "%s",
                "toaster:toasterOutOfBread": {}
              }
            }""", EVENT_TIME), eventListener.readNext(), JSONCompareMode.LENIENT);
    }

    @Test
    void filterNotificationPartialTest() throws Exception {
        final var response = establishFilteredSubscription("""
            <test-notification xmlns="test:notification">
              <entry>
                <id/>
              </entry>
            </test-notification>
            """, streamClient);

        assertEquals(HttpResponseStatus.OK, response.status());
        final var id = extractSubscriptionId(response);
        final var eventListener = startSubscriptionStream(String.valueOf(id));

        final var toasterRestockedNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(ExampleNotification.QNAME))
            .withChild(ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Entry.QNAME))
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Entry.QNAME,
                        QNAME_PROPERTY_ID, "ID"))
                    .withChild(ImmutableNodes.leafNode(QNAME_PROPERTY_ID, "ID"))
                    .withChild(ImmutableNodes.leafNode(QNAME_PROPERTY_NAME, "name"))
                    .build())
                .build())
            .build();
        publishService().putNotification(new DOMNotificationEvent.Rfc6020(toasterRestockedNotification, EVENT_TIME));

        // verify name was filtered out
        JSONAssert.assertEquals(String.format("""
            {
              "ietf-restconf:notification" : {
                "event-time" : "%s",
                "notification-test:example-notification" : {
                  "entry" : [
                    {
                      "id" : "ID"
                    }
                  ]
                }
              }
            }""", EVENT_TIME), eventListener.readNext(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void filterReferenceTest() throws Exception {
        // Establish subscription
        final var response = invokeRequest(HttpMethod.POST,
            "/restconf/data/ietf-subscribed-notifications:filters",
            MediaTypes.APPLICATION_YANG_DATA_XML,
            """
                <stream-filter xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
                 <name>foo</name>
                 <stream-subtree-filter>
                  <toasterOutOfBread xmlns="http://netconfcentral.org/ns/toaster"/>
                 </stream-subtree-filter>
                </stream-filter>""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.CREATED, response.status());

        final var response1 = invokeRequestKeepClient(streamClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json",
                    "stream-filter-name" : "foo"
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);


        // FIXME: add some verification the filter was loaded/used correctly
        final var response2 = invokeRequest(HttpMethod.GET,
            "/restconf/data/ietf-subscribed-notifications:filters",
            MediaTypes.APPLICATION_YANG_DATA_XML, null, MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response2.status());
        assertEquals("foo", response2.content().toString(StandardCharsets.UTF_8));
    }
}
