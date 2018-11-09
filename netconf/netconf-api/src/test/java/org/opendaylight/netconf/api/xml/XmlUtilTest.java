/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import java.io.ByteArrayInputStream;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

public class XmlUtilTest {

    private final String xml = "<top xmlns=\"namespace\">\n" + "<innerText>value</innerText>\n"
        + "<innerPrefixedText xmlns:pref=\"prefixNamespace\">prefix:value</innerPrefixedText>\n"
        + "<innerPrefixedText xmlns=\"randomNamespace\" xmlns:pref=\"prefixNamespace\">prefix:value"
            + "</innerPrefixedText>\n"
        + "</top>";

    @Test
    public void testCreateElement() throws Exception {
        final Document document = XmlUtil.newDocument();
        final Element top = XmlUtil.createElement(document, "top", Optional.of("namespace"));

        top.appendChild(XmlUtil.createTextElement(document, "innerText", "value", Optional.of("namespace")));
        top.appendChild(XmlUtil.createTextElementWithNamespacedContent(document, "innerPrefixedText", "pref",
                "prefixNamespace", "value", Optional.of("namespace")));
        top.appendChild(XmlUtil.createTextElementWithNamespacedContent(document, "innerPrefixedText", "pref",
                "prefixNamespace", "value", Optional.of("randomNamespace")));

        document.appendChild(top);
        assertEquals("top", XmlUtil.createDocumentCopy(document).getDocumentElement().getTagName());

        final Diff diff = DiffBuilder.compare(xml)
                .withTest(document)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void testLoadSchema() throws Exception {
        XmlUtil.loadSchema();
        try {
            XmlUtil.loadSchema(new ByteArrayInputStream(xml.getBytes()));
            fail("Input stream does not contain xsd");
        } catch (final IllegalStateException e) {
            assertTrue(e.getCause() instanceof SAXParseException);
        }

    }

    @Test(expected = SAXParseException.class)
    public void testXXEFlaw() throws Exception {
        XmlUtil.readXmlToDocument(
                "<!DOCTYPE foo [  \n" + "<!ELEMENT foo ANY >\n" + "<!ENTITY xxe SYSTEM \"file:///etc/passwd\" >]>\n"
                        + "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n" + "  <capabilities>\n"
                        + "    <capability>urn:ietf:params:netconf:base:1.0 &xxe;</capability>\n"
                        + "  </capabilities>\n" + "  </hello>]]>]]>");
    }

}
