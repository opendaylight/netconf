/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class XmlElementTest {
    private static final String ELEMENT_AS_STRING = """
        <top xmlns="namespace" xmlns:a="attrNamespace" a:attr1="value1" attr2="value2">
          <inner>
            <deepInner>deepValue</deepInner>
          </inner>
          <innerNamespace xmlns="innerNamespace">innerNamespaceValue</innerNamespace>
          <innerPrefixed xmlns:b="prefixedValueNamespace">b:valueWithPrefix</innerPrefixed>
        </top>""";

    private final Document document;
    private final Element element;
    private final XmlElement xmlElement;

    XmlElementTest() throws Exception {
        document = XmlUtil.readXmlToDocument(ELEMENT_AS_STRING);
        element = document.getDocumentElement();
        xmlElement = XmlElement.fromDomElement(element);
    }

    @Test
    void testConstruct() throws Exception {
        final var fromString = XmlElement.fromString(ELEMENT_AS_STRING);
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
    void testGetters() throws Exception {
        assertEquals(element, xmlElement.getDomElement());
        assertEquals(element.getElementsByTagName("inner").getLength(),
                xmlElement.getElementsByTagName("inner").getLength());

        assertEquals("top", xmlElement.getName());
        assertTrue(xmlElement.hasNamespace());
        assertEquals("namespace", xmlElement.getNamespace());
        assertEquals("namespace", xmlElement.getNamespaceAttribute());

        assertEquals("value1", xmlElement.getAttribute("attr1", "attrNamespace"));
        assertEquals("value2", xmlElement.getAttribute("attr2"));
        assertEquals(2 + 2/*Namespace definition*/, xmlElement.getAttributes().size());

        assertEquals(3, xmlElement.getChildElements().size());
        assertEquals(1, xmlElement.getChildElements("inner").size());
        assertTrue(xmlElement.getOnlyChildElementOptionally("inner").isPresent());
        assertTrue(xmlElement.getOnlyChildElementWithSameNamespaceOptionally("inner").isPresent());
        assertEquals(0, xmlElement.getChildElements("unknown").size());
        assertEquals(Optional.empty(), xmlElement.getOnlyChildElementOptionally("unknown"));
        assertEquals(1, xmlElement.getChildElementsWithinNamespace("innerNamespace", "innerNamespace").size());
        assertTrue(xmlElement.getOnlyChildElementOptionally("innerNamespace", "innerNamespace").isPresent());
        assertEquals(Optional.empty(), xmlElement.getOnlyChildElementOptionally("innerNamespace", "unknownNamespace"));

        final var noNamespaceElement = XmlElement.fromString("<noNamespace/>");
        assertFalse(noNamespaceElement.hasNamespace());

        assertThrows(MissingNameSpaceException.class, () -> noNamespaceElement.getNamespace());

        final var inner = xmlElement.getOnlyChildElement("inner");
        final var deepInner = inner.getOnlyChildElementWithSameNamespaceOptionally().orElseThrow();
        assertEquals(deepInner, inner.getOnlyChildElementWithSameNamespace());
        assertEquals(Optional.empty(), xmlElement.getOnlyChildElementOptionally("unknown"));
        assertEquals("deepValue", deepInner.getTextContent());
        assertEquals("deepValue", deepInner.getOnlyTextContentOptionally().orElseThrow());
        assertEquals("deepValue", deepInner.getOnlyTextContentOptionally().orElseThrow());
    }

    @Test
    void testExtractNamespaces() throws Exception {
        final var innerPrefixed = xmlElement.getOnlyChildElement("innerPrefixed");
        var namespaceOfTextContent = innerPrefixed.findNamespaceOfTextContent();

        assertNotNull(namespaceOfTextContent);
        assertEquals("b", namespaceOfTextContent.getKey());
        assertEquals("prefixedValueNamespace", namespaceOfTextContent.getValue());
        final var innerNamespace = xmlElement.getOnlyChildElement("innerNamespace");
        namespaceOfTextContent = innerNamespace.findNamespaceOfTextContent();

        assertEquals("", namespaceOfTextContent.getKey());
        assertEquals("innerNamespace", namespaceOfTextContent.getValue());
    }
}
