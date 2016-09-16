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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class NetconfNotificationTest {

    @Test
    public void testWrapNotification() throws ParserConfigurationException {
        final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        final Document document = docBuilder.newDocument();

        final Element rootElement = document.createElement("test-root");
        document.appendChild(rootElement);

        final Date eventTime = new Date();
        eventTime.setTime(0);

        final NetconfNotification netconfNotification = new NetconfNotification(document, eventTime);
        final Document resultDoc = netconfNotification.getDocument();
        final NodeList nodeList = resultDoc.getElementsByTagNameNS(NetconfNotification.NOTIFICATION_NAMESPACE, NetconfNotification.NOTIFICATION);

        assertNotNull(nodeList);
        assertEquals(1, nodeList.getLength());

        final Element entireNotification = (Element) nodeList.item(0);
        final NodeList childNodes = entireNotification.getElementsByTagNameNS(NetconfNotification.NOTIFICATION_NAMESPACE, NetconfNotification.EVENT_TIME);

        assertNotNull(childNodes);
        assertEquals(1, childNodes.getLength());

        final Element eventTimeElement = (Element) childNodes.item(0);

        assertEquals("1970-01-01T01:00:00+01:00", eventTimeElement.getTextContent());
        assertEquals(eventTime, netconfNotification.getEventTime());

    }
}
