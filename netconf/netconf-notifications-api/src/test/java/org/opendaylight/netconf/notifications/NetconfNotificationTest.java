/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;
import org.junit.Test;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class NetconfNotificationTest {

    @Test
    public void testWrapNotification() throws Exception {
        final Document document = UntrustedXML.newDocumentBuilder().newDocument();

        final Element rootElement = document.createElement("test-root");
        document.appendChild(rootElement);

        final Date eventTime = new Date();
        eventTime.setTime(10000000);

        final NetconfNotification netconfNotification = new NetconfNotification(document, eventTime);
        final Document resultDoc = netconfNotification.getDocument();
        final NodeList nodeList = resultDoc.getElementsByTagNameNS(NetconfNotification.NOTIFICATION_NAMESPACE,
                NetconfNotification.NOTIFICATION);

        assertNotNull(nodeList);
        // expected only the one NOTIFICATION tag
        assertEquals(1, nodeList.getLength());

        final Element entireNotification = (Element) nodeList.item(0);
        final NodeList childNodes = entireNotification.getElementsByTagNameNS(
                NetconfNotification.NOTIFICATION_NAMESPACE, NetconfNotification.EVENT_TIME);

        assertNotNull(childNodes);
        // expected only the one EVENT_TIME tag
        assertEquals(1, childNodes.getLength());

        final Element eventTimeElement = (Element) childNodes.item(0);

        assertEquals(eventTime.getTime(), NetconfNotification.RFC3339_DATE_PARSER
                .apply(eventTimeElement.getTextContent()).getTime());

        assertEquals(eventTime, netconfNotification.getEventTime());

    }
}
