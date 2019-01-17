/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Collections;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;

public class NetconfUtilTest {

    @BeforeClass
    public static void classSetUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void testConflictingVersionDetection() throws Exception {
        final Document document = XmlUtil.readXmlToDocument(getClass()
                .getResourceAsStream("/netconfMessages/conflictingversion/conflictingVersionResponse.xml"));
        try {
            NetconfUtil.checkIsMessageOk(document);
            fail();
        } catch (final IllegalStateException e) {
            assertThat(e.getMessage(), containsString("Optimistic lock failed. Expected parent version 21, was 18"));
        }
    }

    @Test
    public void testWriteNormalizedNode() throws Exception {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(Collections.singletonList($YangModuleInfoImpl.getInstance()));
        final SchemaContext context = moduleInfoBackedContext.getSchemaContext();
        final LeafNode<?> username = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(Session.QNAME, "username")))
                .withValue("admin")
                .build();
        final MapEntryNode session1 = Builders.mapEntryBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier
                        .NodeIdentifierWithPredicates(Session.QNAME, QName.create(Session.QNAME, "session-id"), 1L))
                .withChild(username)
                .build();
        final MapNode sessionList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Session.QNAME))
                .withChild(session1)
                .build();
        final ContainerNode sessions = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Sessions.QNAME))
                .withChild(sessionList)
                .build();
        final Document actual = XmlUtil.newDocument();
        final SchemaPath path = SchemaPath.create(true, NetconfState.QNAME);
        NetconfUtil.writeNormalizedNode(actual, sessions, context, path);
        final Document expected = XmlUtil.readXmlToDocument(getClass().getResourceAsStream("/sessions.xml"));
        final Diff diff = XMLUnit.compareXML(expected, actual);
        Assert.assertTrue(diff.toString(), diff.similar());
    }
}
