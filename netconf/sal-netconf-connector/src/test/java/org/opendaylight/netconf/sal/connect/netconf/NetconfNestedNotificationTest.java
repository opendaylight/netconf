/*
 * Copyright © 2020 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class NetconfNestedNotificationTest extends AbstractBaseSchemasTest {

    private static final QName INTERFACES_QNAME = QName
            .create("org:opendaylight:notification:test:ns:yang:nested-notification", "2014-07-08", "interfaces");
    private static final QName INTERFACE_QNAME = QName.create(INTERFACES_QNAME, "interface");
    private static final QName INTERFACE_ENABLED_NOTIFICATION_QNAME = QName
            .create(INTERFACE_QNAME, "interface-enabled");

    @Test
    public void testNestedNotificationToNotificationFunction() throws Exception {
        final EffectiveModelContext schemaContext =
                getNotificationSchemaContext(Collections.singleton("/schemas/nested-notification.yang"));
        final NetconfMessage notificationMessage = prepareNotification("/nested-notification-payload.xml");
        NetconfMessageTransformer messageTransformer = new NetconfMessageTransformer(
            new EmptyMountPointContext(schemaContext), true, BASE_SCHEMAS.getBaseSchema());
        final DOMNotification domNotification = messageTransformer.toNotification(notificationMessage);
        final ContainerNode root = domNotification.getBody();
        assertNotNull(root);
        assertEquals(1, Iterables.size(root.getValue()));
        assertEquals("interface-enabled", root.getNodeType().getLocalName());
        assertEquals(NetconfNotification.RFC3339_DATE_PARSER.apply("2008-07-08T00:01:00Z").toInstant(),
                ((DOMEvent) domNotification).getEventInstant());
        assertEquals(Absolute.of(INTERFACES_QNAME, INTERFACE_QNAME, INTERFACE_ENABLED_NOTIFICATION_QNAME),
                domNotification.getType());
    }

    private EffectiveModelContext getNotificationSchemaContext(final Collection<String> yangResources) {
        final EffectiveModelContext context = YangParserTestUtils.parseYangResources(getClass(), yangResources);
        final Collection<? extends Module> modules = context.getModules();
        assertFalse(modules.isEmpty());
        return context;
    }

    private NetconfMessage prepareNotification(final String notificationPayloadPath) throws IOException, SAXException {
        InputStream notifyPayloadStream = getClass().getResourceAsStream(notificationPayloadPath);
        assertNotNull(notifyPayloadStream);

        final Document doc = XmlUtil.readXmlToDocument(notifyPayloadStream);
        assertNotNull(doc);
        return new NetconfMessage(doc);
    }
}
