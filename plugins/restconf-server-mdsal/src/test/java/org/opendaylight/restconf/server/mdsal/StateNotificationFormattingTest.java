/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry.getFormattedNotification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.NoSuchSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspendedReason;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class StateNotificationFormattingTest {
    private static final Instant EVENT_TIME =
        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final String STOP_TIME = "2024-10-30T12:34:56Z";
    private static final String STREAM_NAME = "NETCONF";
    private static final String URI = "http://example.com";
    private static final QName ENCODING_XML = EncodeXml$I.QNAME;
    private static final QName ENCODING_JSON = EncodeJson$I.QNAME;
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/notifications");

    @BeforeAll
    static void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    void subscriptionModifiedStateNotificationJsonTest() throws Exception {
        // FIXME add filter
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionModified(ID, STREAM_NAME, ENCODING_JSON,
            null, STOP_TIME, URI);
        final var jsonFormattedNotification = getFormattedNotification(ID, ENCODING_JSON, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);
        final var expectedNotification = """
            {
              "ietf-restconf:notification":{
                "event-time":"2024-10-30T13:34:56+01:00",
                "ietf-subscribed-notifications:subscription-modified":{
                  "ietf-restconf-subscribed-notifications:uri":"http://example.com",
                  "stream":"NETCONF",
                  "id":2147483648,
                  "stop-time":"2024-10-30T12:34:56Z",
                  "encoding":"ietf-subscribed-notifications:encode-json"
                }
              }
            }""";

        JSONAssert.assertEquals(expectedNotification, jsonFormattedNotification, JSONCompareMode.LENIENT);
    }

    @Test
    void subscriptionModifiedStateNotificationXmlTest() throws Exception {
        // FIXME add filter
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionModified(ID, STREAM_NAME, ENCODING_XML,
            null, STOP_TIME, URI);
        final var xmlFormattedNotification = getFormattedNotification(ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);
        final var expectedNotification = """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
              <eventTime>2024-10-30T13:34:56+01:00</eventTime>
              <subscription-modified xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
                <uri xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf-subscribed-notifications">http://example.com</uri>
                <stream>NETCONF</stream>
                <id>2147483648</id>
                <stop-time>2024-10-30T12:34:56Z</stop-time>
                <encoding>encode-xml</encoding>
              </subscription-modified>
            </notification>""";

        assertTrue(XMLUnit.compareXML(expectedNotification, xmlFormattedNotification).identical());
    }

    @Test
    void subscriptionTerminatedStateNotificationJsonTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionTerminated(ID, NoSuchSubscription.QNAME);
        final var jsonFormattedNotification = getFormattedNotification(ID, ENCODING_JSON, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);
        final var expectedNotification = """
            {
              "ietf-restconf:notification":{
                "event-time":"2024-10-30T13:34:56+01:00",
                "ietf-subscribed-notifications:subscription-terminated":{
                  "id":2147483648,
                  "reason":"ietf-subscribed-notifications:no-such-subscription"
                }
              }
            }""";

        JSONAssert.assertEquals(expectedNotification, jsonFormattedNotification, JSONCompareMode.LENIENT);
    }

    @Test
    void subscriptionTerminatedStateNotificationXmlTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionTerminated(ID, NoSuchSubscription.QNAME);
        final var xmlFormattedNotification = getFormattedNotification(ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);
        final var expectedNotification = """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
              <eventTime>2024-10-30T13:34:56+01:00</eventTime>
              <subscription-terminated xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
                <id>2147483648</id>
                <reason>no-such-subscription</reason>
              </subscription-terminated>
            </notification>""";

        assertTrue(XMLUnit.compareXML(expectedNotification, xmlFormattedNotification).identical());
    }

    @Test
    void subscriptionSuspendedStateNotificationJsonTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionSuspended(ID,
            SubscriptionSuspendedReason.QNAME);
        final var jsonFormattedNotification = getFormattedNotification(ID, ENCODING_JSON, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);
        final var expectedNotification = """
            {
              "ietf-restconf:notification":{
                "event-time":"2024-10-30T13:34:56+01:00",
                "ietf-subscribed-notifications:subscription-suspended":{
                  "id":2147483648,
                  "reason":"ietf-subscribed-notifications:subscription-suspended-reason"
                }
              }
            }""";

        JSONAssert.assertEquals(expectedNotification, jsonFormattedNotification, JSONCompareMode.LENIENT);
    }

    @Test
    void subscriptionSuspendedStateNotificationXmlTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionSuspended(ID,
            SubscriptionSuspendedReason.QNAME);
        final var xmlFormattedNotification = getFormattedNotification(ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);
        final var expectedNotification = """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
              <eventTime>2024-10-30T13:34:56+01:00</eventTime>
              <subscription-suspended xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
                <id>2147483648</id>
                <reason>subscription-suspended-reason</reason>
              </subscription-suspended>
            </notification>""";

        assertTrue(XMLUnit.compareXML(expectedNotification, xmlFormattedNotification).identical());
    }

    @Test
    void subscriptionResumedStateNotificationJsonTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionResumed(ID);
        final var jsonFormattedNotification = getFormattedNotification(ID, ENCODING_JSON, notificationNode,
                MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);
        final var expectedNotification = """
            {
              "ietf-restconf:notification":{
                "event-time":"2024-10-30T13:34:56+01:00",
                "ietf-subscribed-notifications:subscription-resumed":{
                  "id":2147483648
                }
              }
            }""";

        JSONAssert.assertEquals(expectedNotification, jsonFormattedNotification, JSONCompareMode.LENIENT);
    }

    @Test
    void subscriptionResumedStateNotificationXmlTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionResumed(ID);
        final var xmlFormattedNotification = getFormattedNotification(ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);
        final var expectedNotification = """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
              <eventTime>2024-10-30T13:34:56+01:00</eventTime>
              <subscription-resumed xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
                <id>2147483648</id>
              </subscription-resumed>
            </notification>""";

        assertTrue(XMLUnit.compareXML(expectedNotification, xmlFormattedNotification).identical());
    }
}
