/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry.getFormattedNotification;

import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.NoSuchSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspendedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.xml.sax.InputSource;

/**
 * Unit test over logic that creates and formats state notification into string message before it is sent to receivers.
 */
public class StateNotificationFormattingTest {
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/notifications");
    private static final Instant EVENT_TIME =
        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();
    private static final QName ENCODING_XML = EncodeXml$I.QNAME;
    private static final QName ENCODING_JSON = EncodeJson$I.QNAME;
    private static final Uint32 SUBSCRIPTION_ID = Uint32.valueOf(2147483648L);
    private static final String ENCODING = "encoding";
    private static final String STOP_TIME = "2024-10-30T12:34:56Z";
    private static final String STREAM_NAME = "NETCONF";
    private static final String URI = "http://example.com";
    private static final String ID = "id";
    private static final String IETF_RESTCONF_NOTIFICATION = "ietf-restconf:notification";
    private static final String IETF_SUBSCRIBED_NOTIFICATIONS = "ietf-subscribed-notifications";
    private static final String REASON = "reason";
    private static final String STREAM = "stream";

    /**
     * Case over formatting subscription modified state notification to JSON encoded string.
     */
    @Test
    void subscriptionModifiedStateNotificationJsonTest() throws Exception {
        // FIXME add filter and assert it's presence in result JSON
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionModified(SUBSCRIPTION_ID, STREAM_NAME,
            ENCODING_JSON, null, STOP_TIME, URI);
        final var jsonFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_JSON, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);

        final var notification = new JSONObject(jsonFormattedNotification).getJSONObject(IETF_RESTCONF_NOTIFICATION);
        final var time = Instant.parse(notification.getString("event-time"));
        assertEquals(EVENT_TIME, time);

        final var data = notification.getJSONObject(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + SubscriptionModified.QNAME
            .getLocalName());
        assertEquals(URI, data.getString("ietf-restconf-subscribed-notifications:uri"));
        assertEquals(STREAM_NAME, data.getString(STREAM));
        assertEquals(SUBSCRIPTION_ID.intValue(), data.getInt(ID));
        assertEquals(STOP_TIME, data.getString("stop-time"));
        assertEquals(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + ENCODING_JSON.getLocalName(), data.getString(ENCODING));
    }

    /**
     * Case over formatting subscription modified state notification to XML encoded string.
     */
    @Test
    void subscriptionModifiedStateNotificationXmlTest() throws Exception {
        // FIXME add filter and assert it's presence in result XML
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionModified(SUBSCRIPTION_ID, STREAM_NAME,
            ENCODING_XML, null, STOP_TIME, URI);
        final var xmlFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);

        final var factory = DocumentBuilderFactory.newInstance();
        final var builder = factory.newDocumentBuilder();
        final var is = new InputSource(new StringReader(xmlFormattedNotification));
        final var xmlDoc = builder.parse(is).getDocumentElement();

        final var time = Instant.parse(xmlDoc.getElementsByTagName("eventTime").item(0).getTextContent());
        assertEquals(EVENT_TIME, time);
        assertEquals(1, xmlDoc.getElementsByTagName(SubscriptionModified.QNAME.getLocalName()).getLength());
        assertEquals(1, xmlDoc.getElementsByTagName(ID).getLength());
        assertEquals(SUBSCRIPTION_ID, Uint32.valueOf(xmlDoc.getElementsByTagName(ID).item(0).getTextContent()));
        assertEquals(1, xmlDoc.getElementsByTagName("uri").getLength());
        assertEquals(URI, xmlDoc.getElementsByTagName("uri").item(0).getTextContent());
        assertEquals(1, xmlDoc.getElementsByTagName(STREAM).getLength());
        assertEquals(STREAM_NAME, xmlDoc.getElementsByTagName(STREAM).item(0).getTextContent());
        assertEquals(1, xmlDoc.getElementsByTagName("stop-time").getLength());
        assertEquals(STOP_TIME, xmlDoc.getElementsByTagName("stop-time").item(0).getTextContent());
        assertEquals(1, xmlDoc.getElementsByTagName(ENCODING).getLength());
        assertEquals(ENCODING_XML.getLocalName(), xmlDoc.getElementsByTagName(ENCODING).item(0).getTextContent());
    }

    /**
     * Case over formatting subscription terminated state notification to JSON encoded string.
     */
    @Test
    void subscriptionTerminatedStateNotificationJsonTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionTerminated(SUBSCRIPTION_ID,
            NoSuchSubscription.QNAME);
        final var jsonFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_JSON, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);

        final var notification = new JSONObject(jsonFormattedNotification).getJSONObject(IETF_RESTCONF_NOTIFICATION);
        final var time = Instant.parse(notification.getString("event-time"));
        assertEquals(EVENT_TIME, time);

        final var data = notification.getJSONObject(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + SubscriptionTerminated.QNAME
            .getLocalName());
        assertEquals(SUBSCRIPTION_ID.intValue(), data.getInt(ID));
        assertEquals(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + NoSuchSubscription.QNAME.getLocalName(),
            data.getString("reason"));
    }

    /**
     * Case over formatting subscription terminated state notification to XML encoded string.
     */
    @Test
    void subscriptionTerminatedStateNotificationXmlTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionTerminated(SUBSCRIPTION_ID,
            NoSuchSubscription.QNAME);
        final var xmlFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);

        final var factory = DocumentBuilderFactory.newInstance();
        final var builder = factory.newDocumentBuilder();
        final var is = new InputSource(new StringReader(xmlFormattedNotification));
        final var xmlDoc = builder.parse(is).getDocumentElement();

        final var time = Instant.parse(xmlDoc.getElementsByTagName("eventTime").item(0).getTextContent());
        assertEquals(EVENT_TIME, time);
        assertEquals(1, xmlDoc.getElementsByTagName(SubscriptionTerminated.QNAME.getLocalName()).getLength());
        assertEquals(1, xmlDoc.getElementsByTagName(ID).getLength());
        assertEquals(SUBSCRIPTION_ID, Uint32.valueOf(xmlDoc.getElementsByTagName(ID).item(0).getTextContent()));
        assertEquals(1, xmlDoc.getElementsByTagName("reason").getLength());
        assertEquals(NoSuchSubscription.QNAME.getLocalName(), xmlDoc.getElementsByTagName("reason").item(0)
            .getTextContent());
    }

    /**
     * Case over formatting subscription suspended state notification to JSON encoded string.
     */
    @Test
    void subscriptionSuspendedStateNotificationJsonTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionSuspended(SUBSCRIPTION_ID,
            SubscriptionSuspendedReason.QNAME);
        final var jsonFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_JSON, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);

        final var notification = new JSONObject(jsonFormattedNotification).getJSONObject(IETF_RESTCONF_NOTIFICATION);
        final var time = Instant.parse(notification.getString("event-time"));
        assertEquals(EVENT_TIME, time);

        final var data = notification.getJSONObject(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + SubscriptionSuspended.QNAME
            .getLocalName());
        assertEquals(SUBSCRIPTION_ID.intValue(), data.getInt(ID));
        assertEquals(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + SubscriptionSuspendedReason.QNAME.getLocalName(),
            data.getString(REASON));
    }

    /**
     * Case over formatting subscription suspended state notification to XML encoded string.
     */
    @Test
    void subscriptionSuspendedStateNotificationXmlTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionSuspended(SUBSCRIPTION_ID,
            SubscriptionSuspendedReason.QNAME);
        final var xmlFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);

        final var factory = DocumentBuilderFactory.newInstance();
        final var builder = factory.newDocumentBuilder();
        final var is = new InputSource(new StringReader(xmlFormattedNotification));
        final var xmlDoc = builder.parse(is).getDocumentElement();

        final var time = Instant.parse(xmlDoc.getElementsByTagName("eventTime").item(0).getTextContent());
        assertEquals(EVENT_TIME, time);
        assertEquals(1, xmlDoc.getElementsByTagName(SubscriptionSuspended.QNAME.getLocalName()).getLength());
        assertEquals(1, xmlDoc.getElementsByTagName(ID).getLength());
        assertEquals(SUBSCRIPTION_ID, Uint32.valueOf(xmlDoc.getElementsByTagName(ID).item(0).getTextContent()));
        assertEquals(1, xmlDoc.getElementsByTagName(REASON).getLength());
        assertEquals(SubscriptionSuspendedReason.QNAME.getLocalName(), xmlDoc.getElementsByTagName(REASON).item(0)
            .getTextContent());
    }

    /**
     * Case over formatting subscription resumed state notification to JSON encoded string.
     */
    @Test
    void subscriptionResumedStateNotificationJsonTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionResumed(SUBSCRIPTION_ID);
        final var jsonFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_JSON, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);

        final var notification = new JSONObject(jsonFormattedNotification).getJSONObject(IETF_RESTCONF_NOTIFICATION);
        final var time = Instant.parse(notification.getString("event-time"));
        assertEquals(EVENT_TIME, time);
        final var data = notification.getJSONObject(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + SubscriptionResumed.QNAME
            .getLocalName());
        assertEquals(SUBSCRIPTION_ID.intValue(), data.getInt(ID));
    }

    /**
     * Case over formatting subscription resumed state notification to XML encoded string.
     */
    @Test
    void subscriptionResumedStateNotificationXmlTest() throws Exception {
        final var notificationNode = MdsalRestconfStreamRegistry.subscriptionResumed(SUBSCRIPTION_ID);
        final var xmlFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME, MODEL_CONTEXT);

        final var factory = DocumentBuilderFactory.newInstance();
        final var builder = factory.newDocumentBuilder();
        final var is = new InputSource(new StringReader(xmlFormattedNotification));
        final var xmlDoc = builder.parse(is).getDocumentElement();

        final var time = Instant.parse(xmlDoc.getElementsByTagName("eventTime").item(0).getTextContent());
        assertEquals(EVENT_TIME, time);
        assertEquals(1, xmlDoc.getElementsByTagName(SubscriptionResumed.QNAME.getLocalName()).getLength());
        assertEquals(1, xmlDoc.getElementsByTagName(ID).getLength());
        assertEquals(SUBSCRIPTION_ID, Uint32.valueOf(xmlDoc.getElementsByTagName(ID).item(0).getTextContent()));
    }
}
