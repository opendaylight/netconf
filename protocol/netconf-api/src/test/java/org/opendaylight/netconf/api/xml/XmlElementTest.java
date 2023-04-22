/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map.Entry;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XmlElementTest {

    private final String elementAsString =
            "<top xmlns=\"namespace\" xmlns:a=\"attrNamespace\" a:attr1=\"value1\" attr2=\"value2\">"
            + "<inner>" + "<deepInner>deepValue</deepInner>" + "</inner>"
            + "<innerNamespace xmlns=\"innerNamespace\">innerNamespaceValue</innerNamespace>"
            + "<innerPrefixed xmlns:b=\"prefixedValueNamespace\">b:valueWithPrefix</innerPrefixed>" + "</top>";
    private Document document;
    private Element element;
    private XmlElement xmlElement;

    @Before
    public void setUp() throws Exception {
        document = XmlUtil.readXmlToDocument(elementAsString);
        element = document.getDocumentElement();
        xmlElement = XmlElement.fromDomElement(element);
    }

    @Test
    public void testConstruct() throws Exception {
        final XmlElement fromString = XmlElement.fromString(elementAsString);
        assertEquals(fromString, xmlElement);
        XmlElement.fromDomDocument(document);
        XmlElement.fromDomElement(element);
        XmlElement.fromDomElementWithExpected(element, "top");
        XmlElement.fromDomElementWithExpected(element, "top", "namespace");

        assertThrows(DocumentedException.class, () -> XmlElement.fromString("notXml"));
        assertThrows(DocumentedException.class, () -> XmlElement.fromDomElementWithExpected(element, "notTop"));
        assertThrows(DocumentedException.class,
            () -> XmlElement.fromDomElementWithExpected(element, "top", "notNamespace"));
    }

    @Test
    public void testGetters() throws Exception {
        assertEquals(element, xmlElement.getDomElement());
        assertEquals(element.getElementsByTagName("inner").getLength(),
                xmlElement.getElementsByTagName("inner").getLength());

        assertEquals("top", xmlElement.getName());
        assertTrue(xmlElement.hasNamespace());
        assertEquals("namespace", xmlElement.getNamespace());
        assertEquals("namespace", xmlElement.getNamespaceAttribute());
        assertEquals(Optional.of("namespace"), xmlElement.findNamespace());

        assertEquals("value1", xmlElement.getAttribute("attr1", "attrNamespace"));
        assertEquals("value2", xmlElement.getAttribute("attr2"));
        assertEquals(2 + 2/*Namespace definition*/, xmlElement.getAttributes().size());

        assertEquals(3, xmlElement.getChildElements().size());
        assertEquals(1, xmlElement.getChildElements("inner").size());
        assertTrue(xmlElement.getOnlyChildElementOptionally("inner").isPresent());
        assertTrue(xmlElement.getOnlyChildElementWithSameNamespaceOptionally("inner").isPresent());
        assertEquals(0, xmlElement.getChildElements("unknown").size());
        assertFalse(xmlElement.getOnlyChildElementOptionally("unknown").isPresent());
        assertEquals(1, xmlElement.getChildElementsWithSameNamespace("inner").size());
        assertEquals(0, xmlElement.getChildElementsWithSameNamespace("innerNamespace").size());
        assertEquals(1, xmlElement.getChildElementsWithinNamespace("innerNamespace", "innerNamespace").size());
        assertTrue(xmlElement.getOnlyChildElementOptionally("innerNamespace", "innerNamespace").isPresent());
        assertFalse(xmlElement.getOnlyChildElementOptionally("innerNamespace", "unknownNamespace").isPresent());

        final XmlElement noNamespaceElement = XmlElement.fromString("<noNamespace/>");
        assertFalse(noNamespaceElement.hasNamespace());

        assertThrows(MissingNameSpaceException.class, () -> noNamespaceElement.getNamespace());

        final XmlElement inner = xmlElement.getOnlyChildElement("inner");
        final XmlElement deepInner = inner.getOnlyChildElementWithSameNamespaceOptionally().orElseThrow();
        assertEquals(deepInner, inner.getOnlyChildElementWithSameNamespace());
        assertEquals(Optional.empty(), xmlElement.getOnlyChildElementOptionally("unknown"));
        assertEquals("deepValue", deepInner.getTextContent());
        assertEquals("deepValue", deepInner.getOnlyTextContentOptionally().orElseThrow());
        assertEquals("deepValue", deepInner.getOnlyTextContentOptionally().orElseThrow());
    }

    @Test
    public void testExtractNamespaces() throws Exception {
        final XmlElement innerPrefixed = xmlElement.getOnlyChildElement("innerPrefixed");
        Entry<String, String> namespaceOfTextContent = innerPrefixed.findNamespaceOfTextContent();

        assertNotNull(namespaceOfTextContent);
        assertEquals("b", namespaceOfTextContent.getKey());
        assertEquals("prefixedValueNamespace", namespaceOfTextContent.getValue());
        final XmlElement innerNamespace = xmlElement.getOnlyChildElement("innerNamespace");
        namespaceOfTextContent = innerNamespace.findNamespaceOfTextContent();

        assertEquals("", namespaceOfTextContent.getKey());
        assertEquals("innerNamespace", namespaceOfTextContent.getValue());
    }

    @Test
    public void testUnrecognisedElements() throws Exception {
        xmlElement.checkUnrecognisedElements(xmlElement.getOnlyChildElement("inner"),
                xmlElement.getOnlyChildElement("innerPrefixed"), xmlElement.getOnlyChildElement("innerNamespace"));

        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> xmlElement.checkUnrecognisedElements(xmlElement.getOnlyChildElement("inner")));
        assertThat(e.getMessage(),
            // FIXME: this looks very suspect
            both(containsString("innerNamespace")).and(containsString("innerNamespace")));
    }
}
