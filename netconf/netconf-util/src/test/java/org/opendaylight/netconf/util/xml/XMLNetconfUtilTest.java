/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util.xml;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class XMLNetconfUtilTest {

    @BeforeClass
    public static void classSetUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    private static final String NETCONF_MONITORING_NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring";

    @Test
    public void testXPath() throws Exception {
        final XPathExpression correctXPath = XMLNetconfUtil.compileXPath("/top/innerText");
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> XMLNetconfUtil.compileXPath("!@(*&$!"));
        assertThat(ex.getMessage(), startsWith("Error while compiling xpath expression "));
        final Object value = XmlUtil.evaluateXPath(correctXPath,
                XmlUtil.readXmlToDocument("<top><innerText>value</innerText></top>"), XPathConstants.NODE);
        assertEquals("value", ((Element) value).getTextContent());
    }

    @Test
    public void testFilterDomNamespaces() throws IOException, SAXException {
        final var source = XmlUtil.readXmlToDocument(
                getClass().getResourceAsStream("/xml/netconf-state.xml"));
        final var expected = XmlUtil.readXmlToDocument(
                getClass().getResourceAsStream("/xml/netconf-state-filtered.xml"));

        final var filteredDom = XMLNetconfUtil.filterDomNamespaces(
                new DOMSource(source.getDocumentElement()), Set.of(NETCONF_MONITORING_NAMESPACE));
        final var filtered = XmlUtil.newDocument();
        filtered.appendChild(filtered.importNode(filteredDom.getNode(), true));

        final var diff = XMLUnit.compareXML(filtered, expected);
        assertTrue(diff.toString(), diff.similar());
    }
}
