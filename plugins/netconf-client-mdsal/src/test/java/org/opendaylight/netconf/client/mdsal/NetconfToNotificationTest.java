/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

public class NetconfToNotificationTest extends AbstractBaseSchemasTest {

    NetconfMessageTransformer messageTransformer;

    NetconfMessage userNotification;

    @Before
    public void setup() throws Exception {
        InputStream notifyPayloadStream = getClass().getResourceAsStream("/notification-payload.xml");
        assertNotNull(notifyPayloadStream);

        final Document doc = XmlUtil.readXmlToDocument(notifyPayloadStream);
        assertNotNull(doc);
        userNotification = new NetconfMessage(doc);
    }

    static EffectiveModelContext getNotificationSchemaContext(final Class<?> loadClass,
            final boolean getExceptionTest) {
        final EffectiveModelContext context;
        if (getExceptionTest) {
            context = YangParserTestUtils.parseYangResources(loadClass, "/schemas/user-notification4.yang",
                    "/schemas/user-notification3.yang");
        } else {
            context = YangParserTestUtils.parseYangResources(loadClass, "/schemas/user-notification.yang",
                "/schemas/user-notification2.yang");
        }

        final Collection<? extends Module> modules = context.getModules();
        assertTrue(!modules.isEmpty());
        assertNotNull(context);
        return context;
    }

    @Test
    public void testMostRecentWrongYangModel() throws Exception {
        final EffectiveModelContext schemaContext = getNotificationSchemaContext(getClass(), true);
        messageTransformer = new NetconfMessageTransformer(MountPointContext.of(schemaContext), true,
            BASE_SCHEMAS.getBaseSchema());
        assertThrows(IllegalArgumentException.class, () -> messageTransformer.toNotification(userNotification));
    }

    @Test
    public void testToNotificationFunction() throws Exception {
        final EffectiveModelContext schemaContext = getNotificationSchemaContext(getClass(), false);
        messageTransformer = new NetconfMessageTransformer(MountPointContext.of(schemaContext), true,
            BASE_SCHEMAS.getBaseSchema());
        final DOMNotification domNotification = messageTransformer.toNotification(userNotification);
        final ContainerNode root = domNotification.getBody();
        assertNotNull(root);
        assertEquals(6, root.body().size());
        assertEquals("user-visited-page", root.name().getNodeType().getLocalName());
        assertEquals(NotificationMessage.RFC3339_DATE_PARSER.apply("2015-10-23T09:42:27.67175+00:00"),
                ((DOMEvent) domNotification).getEventInstant());
    }
}
