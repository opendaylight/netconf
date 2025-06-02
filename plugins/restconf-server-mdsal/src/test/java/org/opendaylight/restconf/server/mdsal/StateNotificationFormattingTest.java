/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry.getFormattedNotification;

import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.NoSuchSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspendedReason;
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
    private static final Instant EVENT_TIME_VALUE =
        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();
    private static final QName ENCODING_XML = EncodeXml$I.QNAME;
    private static final QName ENCODING_JSON = EncodeJson$I.QNAME;
    private static final Uint32 SUBSCRIPTION_ID = Uint32.valueOf(2147483648L);
    private static final String ENCODING = "encoding";
    private static final String STOP_TIME_VALUE = "2024-10-30T12:34:56Z";
    private static final String STREAM_NAME = "NETCONF";
    private static final String URI_VALUE = "http://example.com";
    private static final String ID = "id";
    private static final String IETF_RESTCONF_NOTIFICATION = "ietf-restconf:notification";
    private static final String IETF_SUBSCRIBED_NOTIFICATIONS = "ietf-subscribed-notifications";
    private static final String REASON = "reason";
    private static final String STREAM = "stream";
    private static final String EVENT_TIME = "event-time";
    private static final String EVENTTIME = "eventTime";
    private static final String STOP_TIME = "stop-time";
    private static final String URI = "uri";

    /**
     * Case over formatting subscription state notifications to XML encoded string.
     */
    @ParameterizedTest
    @EnumSource(MdsalRestconfStreamRegistry.State.class)
    void testSubscriptionStateEventsXml(final MdsalRestconfStreamRegistry.State type) throws Exception {
        final var notificationNode = switch (type) {
            case MODIFIED -> MdsalRestconfStreamRegistry.subscriptionModified(SUBSCRIPTION_ID, STREAM_NAME,
                ENCODING_XML, null, STOP_TIME, URI);
            case RESUMED -> MdsalRestconfStreamRegistry.subscriptionResumed(SUBSCRIPTION_ID);
            case TERMINATED -> MdsalRestconfStreamRegistry.subscriptionTerminated(SUBSCRIPTION_ID,
                NoSuchSubscription.QNAME);
            case SUSPENDED -> MdsalRestconfStreamRegistry.subscriptionSuspended(SUBSCRIPTION_ID,
                SubscriptionSuspendedReason.QNAME);
        };

        final var xmlFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_XML, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME_VALUE, MODEL_CONTEXT);
        assertNotNull(xmlFormattedNotification);
        assertFalse(xmlFormattedNotification.isBlank());

        final var factory = DocumentBuilderFactory.newInstance();
        final var builder = factory.newDocumentBuilder();
        final var is = new InputSource(new StringReader(xmlFormattedNotification));
        final var xmlDoc = builder.parse(is).getDocumentElement();
        final var time = Instant.parse(xmlDoc.getElementsByTagName(EVENTTIME).item(0).getTextContent());
        assertEquals(EVENT_TIME_VALUE, time);
        assertEquals(1, xmlDoc.getElementsByTagName(SubscriptionResumed.QNAME.getLocalName()).getLength());
        assertEquals(1, xmlDoc.getElementsByTagName(ID).getLength());
        assertEquals(SUBSCRIPTION_ID, Uint32.valueOf(xmlDoc.getElementsByTagName(ID).item(0).getTextContent()));
        assertEquals(1, xmlDoc.getElementsByTagName(type.nodeId.getNodeType().getLocalName()).getLength());

        switch (type) {
            case null -> throw new NullPointerException();
            case MODIFIED -> {
                assertEquals(1, xmlDoc.getElementsByTagName(URI).getLength());
                assertEquals(URI_VALUE, xmlDoc.getElementsByTagName(URI).item(0).getTextContent());
                assertEquals(1, xmlDoc.getElementsByTagName(STREAM).getLength());
                assertEquals(STREAM_NAME, xmlDoc.getElementsByTagName(STREAM).item(0).getTextContent());
                assertEquals(1, xmlDoc.getElementsByTagName(STOP_TIME).getLength());
                assertEquals(STOP_TIME_VALUE, xmlDoc.getElementsByTagName(STOP_TIME).item(0).getTextContent());
                assertEquals(1, xmlDoc.getElementsByTagName(ENCODING).getLength());
                assertEquals(ENCODING_XML.getLocalName(), xmlDoc.getElementsByTagName(ENCODING).item(0)
                    .getTextContent());
            }
            case TERMINATED -> {
                assertEquals(1, xmlDoc.getElementsByTagName(REASON).getLength());
                assertEquals(NoSuchSubscription.QNAME.getLocalName(), xmlDoc.getElementsByTagName(REASON)
                    .item(0).getTextContent());
            }
            case SUSPENDED -> {
                assertEquals(1, xmlDoc.getElementsByTagName(REASON).getLength());
                assertEquals(SubscriptionSuspendedReason.QNAME.getLocalName(), xmlDoc.getElementsByTagName(REASON)
                    .item(0).getTextContent());
            }
            case RESUMED -> {
                // Nothing else
            }
        }
    }

    /**
     * Case over formatting subscription state notifications to JSON encoded string.
     */
    @ParameterizedTest
    @EnumSource(MdsalRestconfStreamRegistry.State.class)
    void testSubscriptionStateEventsJson(final MdsalRestconfStreamRegistry.State type) {
        final var notificationNode = switch (type) {
            case MODIFIED -> MdsalRestconfStreamRegistry.subscriptionModified(SUBSCRIPTION_ID, STREAM_NAME,
                ENCODING_JSON, null, STOP_TIME, URI);
            case RESUMED -> MdsalRestconfStreamRegistry.subscriptionResumed(SUBSCRIPTION_ID);
            case TERMINATED -> MdsalRestconfStreamRegistry.subscriptionTerminated(SUBSCRIPTION_ID,
                NoSuchSubscription.QNAME);
            case SUSPENDED -> MdsalRestconfStreamRegistry.subscriptionSuspended(SUBSCRIPTION_ID,
                SubscriptionSuspendedReason.QNAME);
        };

        final var jsonFormattedNotification = getFormattedNotification(SUBSCRIPTION_ID, ENCODING_JSON, notificationNode,
            MdsalRestconfStreamRegistry.State.MODIFIED, EVENT_TIME_VALUE, MODEL_CONTEXT);
        assertNotNull(jsonFormattedNotification);
        assertFalse(jsonFormattedNotification.isBlank());

        final var notification = new JSONObject(jsonFormattedNotification).getJSONObject(IETF_RESTCONF_NOTIFICATION);
        final var time = Instant.parse(notification.getString(EVENT_TIME));
        assertEquals(EVENT_TIME_VALUE, time);
        final var data = notification.getJSONObject(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + type.nodeId.getNodeType()
            .getLocalName());
        assertEquals(SUBSCRIPTION_ID.intValue(), data.getInt(ID));

        switch (type) {
            case null -> throw new NullPointerException();
            case MODIFIED -> {
                assertEquals(URI_VALUE, data.getString("ietf-restconf-subscribed-notifications:" + URI));
                assertEquals(STREAM_NAME, data.getString(STREAM));
                assertEquals(SUBSCRIPTION_ID.intValue(), data.getInt(ID));
                assertEquals(STOP_TIME_VALUE, data.getString(STOP_TIME));
                assertEquals(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + ENCODING_JSON.getLocalName(),
                    data.getString(ENCODING));
            }
            case TERMINATED -> assertEquals(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + NoSuchSubscription.QNAME
                .getLocalName(), data.getString(REASON));
            case SUSPENDED -> assertEquals(IETF_SUBSCRIBED_NOTIFICATIONS + ":" + SubscriptionSuspendedReason.QNAME
                .getLocalName(), data.getString(REASON));
            case RESUMED -> {
                // Nothing else
            }
        }
    }
}
