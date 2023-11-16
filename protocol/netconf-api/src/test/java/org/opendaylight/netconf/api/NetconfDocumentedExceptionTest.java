/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.Iterators;
import java.util.Iterator;
import java.util.Map;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Node;

/**
 * Unit tests for NetconfDocumentedException.
 *
 * @author Thomas Pantelis
 */
class NetconfDocumentedExceptionTest {
    private static final XPathFactory FACTORY = XPathFactory.newInstance();
    private static final NamespaceContext NS_CONTEXT = new NamespaceContext() {
        @Override
        public Iterator<String> getPrefixes(final String namespaceURI) {
            return Iterators.singletonIterator("netconf");
        }

        @Override
        public String getPrefix(final String namespaceURI) {
            assertEquals(NamespaceURN.BASE, namespaceURI);
            return "netconf";
        }

        @Override
        public String getNamespaceURI(final String prefix) {
            return NamespaceURN.BASE;
        }
    };

    private final XPath xpath;

    NetconfDocumentedExceptionTest() {
        xpath = FACTORY.newXPath();
        xpath.setNamespaceContext(NS_CONTEXT);
    }

    @Test
    void testToAndFromXMLDocument() {
        final var errorMessage = "mock error message";
        final var doc = new NetconfDocumentedException(errorMessage, null, ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
            ErrorSeverity.WARNING, Map.of("foo", "bar"))
            .toXMLDocument();
        assertNotNull(doc);

        final var rootNode = doc.getDocumentElement();
        assertEquals("urn:ietf:params:xml:ns:netconf:base:1.0", rootNode.getNamespaceURI());
        assertEquals("rpc-reply", rootNode.getLocalName());

        final var rpcErrorNode = assertNode("/netconf:rpc-reply/netconf:rpc-error", rootNode);
        final var errorTypeNode = assertNode("netconf:error-type", rpcErrorNode);
        assertEquals(ErrorType.PROTOCOL.elementBody(), errorTypeNode.getTextContent());

        final var errorTagNode = assertNode("netconf:error-tag", rpcErrorNode);
        assertEquals(ErrorTag.DATA_EXISTS.elementBody(), errorTagNode.getTextContent());

        final var errorSeverityNode = assertNode("netconf:error-severity", rpcErrorNode);
        assertEquals(ErrorSeverity.WARNING.elementBody(), errorSeverityNode.getTextContent());

        final var errorInfoNode = assertNode("netconf:error-info/netconf:foo", rpcErrorNode);
        assertEquals("bar", errorInfoNode.getTextContent());

        final var errorMsgNode = assertNode("netconf:error-message", rpcErrorNode);
        assertEquals(errorMessage, errorMsgNode.getTextContent());

        // Test fromXMLDocument
        final var de = DocumentedException.fromXMLDocument(doc);
        assertNotNull(de);
        assertEquals(ErrorSeverity.WARNING, de.getErrorSeverity());
        assertEquals(ErrorTag.DATA_EXISTS, de.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, de.getErrorType());
        assertEquals(errorMessage, de.getLocalizedMessage());
        assertEquals(Map.of("foo", "bar"), de.getErrorInfo());
    }

    Node assertNode(final String xpathExp, final Node item) {
        try {
            return assertInstanceOf(Node.class, xpath.compile(xpathExp).evaluate(item, XPathConstants.NODE));
        } catch (XPathExpressionException e) {
            throw new AssertionError(e);
        }
    }
}

