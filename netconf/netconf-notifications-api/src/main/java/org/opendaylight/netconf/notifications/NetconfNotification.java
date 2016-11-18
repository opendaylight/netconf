/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.function.Function;
import org.opendaylight.netconf.api.NetconfMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Special kind of netconf message that contains a timestamp.
 */
public final class NetconfNotification extends NetconfMessage {

    public static final String NOTIFICATION = "notification";
    public static final String NOTIFICATION_NAMESPACE = "urn:ietf:params:netconf:capability:notification:1.0";

    public static final Function<Date, String> RFC3339_DATE_FORMATTER = date ->
            DateTimeFormatter.ISO_DATE_TIME.format(date.toInstant().atOffset(ZoneOffset.UTC));
    public static final Function<String, Date> RFC3339_DATE_PARSER = time -> {
        ZonedDateTime zdt = ZonedDateTime.ofInstant(
                LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME),
                ZoneOffset.UTC,
                ZoneId.systemDefault());
        return Date.from(Instant.from(zdt));
    };

    public static final String EVENT_TIME = "eventTime";

    /**
     * Used for unknown/un-parse-able event-times
     */
    public static final Date UNKNOWN_EVENT_TIME = Date.from(Instant.ofEpochMilli(0));

    private final Date eventTime;

    /**
     * Create new notification and capture the timestamp in the constructor
     */
    public NetconfNotification(final Document notificationContent) {
        this(notificationContent, new Date());
    }

    /**
     * Create new notification with provided timestamp
     */
    public NetconfNotification(final Document notificationContent, final Date eventTime) {
        super(wrapNotification(notificationContent, eventTime));
        this.eventTime = eventTime;
    }

    /**
     * @return notification event time
     */
    public Date getEventTime() {
        return eventTime;
    }

    private static Document wrapNotification(final Document notificationContent, final Date eventTime) {
        Preconditions.checkNotNull(notificationContent);
        Preconditions.checkNotNull(eventTime);

        final Element baseNotification = notificationContent.getDocumentElement();
        final Element entireNotification = notificationContent.createElementNS(NOTIFICATION_NAMESPACE, NOTIFICATION);
        entireNotification.appendChild(baseNotification);

        final Element eventTimeElement = notificationContent.createElementNS(NOTIFICATION_NAMESPACE, EVENT_TIME);
        eventTimeElement.setTextContent(getSerializedEventTime(eventTime));
        entireNotification.appendChild(eventTimeElement);

        notificationContent.appendChild(entireNotification);
        return notificationContent;
    }

    private static String getSerializedEventTime(final Date eventTime) {
        return RFC3339_DATE_FORMATTER.apply(eventTime);
    }
}
