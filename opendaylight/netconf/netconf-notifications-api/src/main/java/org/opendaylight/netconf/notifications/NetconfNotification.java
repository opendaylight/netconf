/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications;

import com.google.common.base.Preconditions;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.util.NetconfConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Special kind of netconf message that contains a timestamp.
 */
public final class NetconfNotification extends NetconfMessage {

     /**
     * Used for unknown/un-parse-able event-times
     */
    public static final Date UNKNOWN_EVENT_TIME = new Date(0);

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
    }

    private static Document wrapNotification(final Document notificationContent, final Date eventTime) {
        Preconditions.checkNotNull(notificationContent);
        Preconditions.checkNotNull(eventTime);

        final Element baseNotification = notificationContent.getDocumentElement();
        final Element entireNotification = notificationContent.createElementNS(NetconfConstants.NOTIFICATION_NAMESPACE, NetconfConstants.NOTIFICATION);
        entireNotification.appendChild(baseNotification);

        final Element eventTimeElement = notificationContent.createElementNS(NetconfConstants.NOTIFICATION_NAMESPACE, NetconfConstants.EVENT_TIME);
        eventTimeElement.setTextContent(getSerializedEventTime(eventTime));
        entireNotification.appendChild(eventTimeElement);

        notificationContent.appendChild(entireNotification);
        return notificationContent;
    }

    private static String getSerializedEventTime(final Date eventTime) {
        // SimpleDateFormat is not threadsafe, cannot be in a constant
        return new SimpleDateFormat(NetconfConstants.RFC3339_DATE_FORMAT_BLUEPRINT).format(eventTime);
    }
}
