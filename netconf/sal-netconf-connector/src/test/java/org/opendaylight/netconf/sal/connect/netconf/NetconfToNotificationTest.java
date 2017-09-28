/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.dom.api.DOMEvent;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

public class NetconfToNotificationTest {

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

    static SchemaContext getNotificationSchemaContext(final Class<?> loadClass,
                                                      final boolean getExceptionTest) throws Exception {
        final List<File> modelsToParse = new ArrayList<>();

        if (getExceptionTest) {
            modelsToParse.add(new File(loadClass.getResource("/schemas/user-notification4.yang").toURI()));
            modelsToParse.add(new File(loadClass.getResource("/schemas/user-notification3.yang").toURI()));
        } else {
            modelsToParse.add(new File(loadClass.getResource("/schemas/user-notification.yang").toURI()));
            modelsToParse.add(new File(loadClass.getResource("/schemas/user-notification2.yang").toURI()));
        }

        final SchemaContext context = YangParserTestUtils.parseYangFiles(modelsToParse);
        final Set<Module> modules = context.getModules();
        assertTrue(!modules.isEmpty());
        assertNotNull(context);
        return context;
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testMostRecentWrongYangModel() throws Exception {
        final SchemaContext schemaContext = getNotificationSchemaContext(getClass(), true);
        messageTransformer = new NetconfMessageTransformer(schemaContext, true);
        messageTransformer.toNotification(userNotification);
    }

    @Test
    public void testToNotificationFunction() throws Exception {
        final SchemaContext schemaContext = getNotificationSchemaContext(getClass(), false);
        messageTransformer = new NetconfMessageTransformer(schemaContext, true);
        final DOMNotification domNotification = messageTransformer.toNotification(userNotification);
        final ContainerNode root = domNotification.getBody();
        assertNotNull(root);
        assertEquals(6, Iterables.size(root.getValue()));
        assertEquals("user-visited-page", root.getNodeType().getLocalName());
        assertEquals(NetconfNotification.RFC3339_DATE_PARSER.apply("2015-10-23T09:42:27.67175+00:00"),
                ((DOMEvent) domNotification).getEventTime());
    }
}
