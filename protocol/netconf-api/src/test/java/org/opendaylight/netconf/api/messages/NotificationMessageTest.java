/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import org.junit.Test;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class NotificationMessageTest {
    @Test
    public void testWrapNotification() throws Exception {
        final Document document = UntrustedXML.newDocumentBuilder().newDocument();

        final Element rootElement = document.createElement("test-root");
        document.appendChild(rootElement);

        final Instant eventTime = Instant.ofEpochMilli(10_000_000);

        final NotificationMessage netconfNotification = new NotificationMessage(document, eventTime);
        final Document resultDoc = netconfNotification.getDocument();
        final NodeList nodeList = resultDoc.getElementsByTagNameNS(
            "urn:ietf:params:xml:ns:netconf:notification:1.0", "notification");

        assertNotNull(nodeList);
        // expected only the one NOTIFICATION tag
        assertEquals(1, nodeList.getLength());

        final Element entireNotification = (Element) nodeList.item(0);
        final NodeList childNodes = entireNotification.getElementsByTagNameNS(
            "urn:ietf:params:xml:ns:netconf:notification:1.0", "eventTime");

        assertNotNull(childNodes);
        // expected only the one EVENT_TIME tag
        assertEquals(1, childNodes.getLength());

        final Element eventTimeElement = (Element) childNodes.item(0);

        assertEquals(eventTime, NotificationMessage.RFC3339_DATE_PARSER.apply(eventTimeElement.getTextContent()));
        assertEquals(eventTime, netconfNotification.getEventTime());
    }
}
