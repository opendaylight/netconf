/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class NotificationMessageTest {
    private static final Instant EVENT_TIME = Instant.ofEpochMilli(10_000_000);

    private final Document document = UntrustedXML.newDocumentBuilder().newDocument();

    @Test
    void testWrapNotification() {
        final var rootElement = document.createElement("test-root");
        document.appendChild(rootElement);

        final var netconfNotification = NotificationMessage.ofNotificationContent(document, EVENT_TIME);
        final var resultDoc = netconfNotification.getDocument();
        final var nodeList = resultDoc.getElementsByTagNameNS(
            "urn:ietf:params:xml:ns:netconf:notification:1.0", "notification");

        assertNotNull(nodeList);
        // expected only the one NOTIFICATION tag
        assertEquals(1, nodeList.getLength());

        final var entireNotification = (Element) nodeList.item(0);
        final var childNodes = entireNotification.getElementsByTagNameNS(
            "urn:ietf:params:xml:ns:netconf:notification:1.0", "eventTime");

        assertNotNull(childNodes);
        // expected only the one EVENT_TIME tag
        assertEquals(1, childNodes.getLength());

        final var eventTimeElement = (Element) childNodes.item(0);

        assertEquals(EVENT_TIME, NotificationMessage.RFC3339_DATE_PARSER.apply(eventTimeElement.getTextContent()));
        assertEquals(EVENT_TIME, netconfNotification.getEventTime());
    }
}
