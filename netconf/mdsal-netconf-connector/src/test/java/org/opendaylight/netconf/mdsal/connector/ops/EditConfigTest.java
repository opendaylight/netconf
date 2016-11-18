/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Created by adetalhouet on 2016-11-18.
 */
public class EditConfigTest {

    private EditConfig editConfig;

    @Before
    public void setup() {
        editConfig = new EditConfig("test_edit-config", Mockito.mock(CurrentSchemaContext.class),
                Mockito.mock(TransactionProvider.class));
    }

    @Test
    public void testBUG7176_withoutPrefix() throws Exception {
        String stringWithoutPrefix =
                "<rpc xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"0\">\n" +
                "  <edit-config>\n" +
                "    <target>\n" +
                "      <candidate/>\n" +
                "    </target>\n" +
                "  </edit-config>\n" +
                "</rpc>";

        XmlElement xe = getXmlElement(stringWithoutPrefix);

        Datastore datastore = editConfig.extractTargetParameter(xe);
        Assert.assertEquals("candidate", datastore.name());
    }

    @Test
    public void testBUG7176_withPrefix() throws Exception {
        String stringWithPrefix =
                "<nc:rpc xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"0\">\n" +
                "  <nc:edit-config>\n" +
                "    <nc:target>\n" +
                "      <nc:candidate/>\n" +
                "    </nc:target>\n" +
                "  </nc:edit-config>\n" +
                "</nc:rpc>";

        XmlElement xe = getXmlElement(stringWithPrefix);

        Datastore datastore = editConfig.extractTargetParameter(xe);
        Assert.assertEquals("candidate", datastore.name());
    }

    @Test
    public void testBUG7176_noTarget() throws Exception {
        String stringWithoutTarget =
                "<nc:rpc xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"0\">\n" +
                "  <nc:edit-config>\n" +
                "    <nc:target>\n" +
                "    </nc:target>\n" +
                "  </nc:edit-config>\n" +
                "</nc:rpc>";

        XmlElement xe = getXmlElement(stringWithoutTarget);

        try {
            editConfig.extractTargetParameter(xe);
        } catch (DocumentedException documentedException) {
            // Ignore
            return;
        }

        Assert.fail("Not specified target, we should fail");
    }

    private final XmlElement getXmlElement(final String elementAsString) throws Exception {
        Document d = XmlUtil.readXmlToDocument(elementAsString);
        Element e = d.getDocumentElement();
        return XmlElement.fromDomElement(e);
    }
}
