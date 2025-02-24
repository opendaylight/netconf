/*
 * Copyright © 2020 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

class NetconfNestedNotificationTest extends AbstractBaseSchemasTest {
    private static final QName INTERFACES_QNAME = QName
            .create("org:opendaylight:notification:test:ns:yang:nested-notification", "2014-07-08", "interfaces");
    private static final QName INTERFACE_QNAME = QName.create(INTERFACES_QNAME, "interface");
    private static final QName INTERFACE_ENABLED_NOTIFICATION_QNAME = QName
            .create(INTERFACE_QNAME, "interface-enabled");

    @Test
    void testNestedNotificationToNotificationFunction() throws Exception {
        final var context = YangParserTestUtils.parseYangResources(
            NetconfNestedNotificationTest.class, "/schemas/nested-notification.yang");

        final NetconfMessage notificationMessage = prepareNotification("/nested-notification-payload.xml");
        NetconfMessageTransformer messageTransformer = new NetconfMessageTransformer(
            DatabindContext.ofModel(context), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())));
        final DOMNotification domNotification = messageTransformer.toNotification(notificationMessage);
        final ContainerNode root = domNotification.getBody();
        assertNotNull(root);
        assertEquals(1, root.body().size());
        assertEquals("interface-enabled", root.name().getNodeType().getLocalName());
        assertEquals(NotificationMessage.RFC3339_DATE_PARSER.apply("2008-07-08T00:01:00Z"),
                ((DOMEvent) domNotification).getEventInstant());
        assertEquals(Absolute.of(INTERFACES_QNAME, INTERFACE_QNAME, INTERFACE_ENABLED_NOTIFICATION_QNAME),
                domNotification.getType());
    }

    private NetconfMessage prepareNotification(final String notificationPayloadPath) throws Exception {
        InputStream notifyPayloadStream = getClass().getResourceAsStream(notificationPayloadPath);
        assertNotNull(notifyPayloadStream);

        final Document doc = XmlUtil.readXmlToDocument(notifyPayloadStream);
        assertNotNull(doc);
        return new NetconfMessage(doc);
    }
}
