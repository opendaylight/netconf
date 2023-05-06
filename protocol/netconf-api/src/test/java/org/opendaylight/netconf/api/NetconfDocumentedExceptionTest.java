/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.Iterators;
import java.util.Iterator;
import java.util.Map;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;
import org.w3c.dom.Node;


/**
 * Unit tests for NetconfDocumentedException.
 *
 * @author Thomas Pantelis
 */
public class NetconfDocumentedExceptionTest {

    private XPath xpath;

    @Before
    public void setUp() throws Exception {
        final XPathFactory xPathfactory = XPathFactory.newInstance();
        xpath = xPathfactory.newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public Iterator<String> getPrefixes(final String namespaceURI) {
                return Iterators.singletonIterator("netconf");
            }

            @Override
            public String getPrefix(final String namespaceURI) {
                return "netconf";
            }

            @Override
            public String getNamespaceURI(final String prefix) {
                return NamespaceURN.BASE;
            }
        });
    }

    @Test
    public void testToAndFromXMLDocument() throws XPathExpressionException {
        final String errorMessage = "mock error message";
        DocumentedException ex = new NetconfDocumentedException(errorMessage, null, ErrorType.PROTOCOL,
                ErrorTag.DATA_EXISTS, ErrorSeverity.WARNING, Map.of("foo", "bar"));

        final Document doc = ex.toXMLDocument();
        assertNotNull("Document is null", doc);

        final Node rootNode = doc.getDocumentElement();

        assertEquals("getNamespaceURI", "urn:ietf:params:xml:ns:netconf:base:1.0", rootNode.getNamespaceURI());
        assertEquals("getLocalName", "rpc-reply", rootNode.getLocalName());

        final Node rpcErrorNode = getNode("/netconf:rpc-reply/netconf:rpc-error", rootNode);
        assertNotNull("rpc-error not found", rpcErrorNode);

        final Node errorTypeNode = getNode("netconf:error-type", rpcErrorNode);
        assertNotNull("error-type not found", errorTypeNode);
        assertEquals("error-type", ErrorType.PROTOCOL.elementBody(), errorTypeNode.getTextContent());

        final Node errorTagNode = getNode("netconf:error-tag", rpcErrorNode);
        assertNotNull("error-tag not found", errorTagNode);
        assertEquals("error-tag", ErrorTag.DATA_EXISTS.elementBody(), errorTagNode.getTextContent());

        final Node errorSeverityNode = getNode("netconf:error-severity", rpcErrorNode);
        assertNotNull("error-severity not found", errorSeverityNode);
        assertEquals("error-severity", ErrorSeverity.WARNING.elementBody(), errorSeverityNode.getTextContent());

        final Node errorInfoNode = getNode("netconf:error-info/netconf:foo", rpcErrorNode);
        assertNotNull("foo not found", errorInfoNode);
        assertEquals("foo", "bar", errorInfoNode.getTextContent());

        final Node errorMsgNode = getNode("netconf:error-message", rpcErrorNode);
        assertNotNull("error-message not found", errorMsgNode);
        assertEquals("error-message", errorMessage, errorMsgNode.getTextContent());

        // Test fromXMLDocument

        ex = DocumentedException.fromXMLDocument(doc);

        assertNotNull("NetconfDocumentedException is null", ex);
        assertEquals("getErrorSeverity", ErrorSeverity.WARNING, ex.getErrorSeverity());
        assertEquals("getErrorTag", ErrorTag.DATA_EXISTS, ex.getErrorTag());
        assertEquals("getErrorType", ErrorType.PROTOCOL, ex.getErrorType());
        assertEquals("getLocalizedMessage", errorMessage, ex.getLocalizedMessage());
        assertEquals("getErrorInfo", Map.of("foo", "bar"), ex.getErrorInfo());
    }

    @SuppressWarnings("unchecked")
    <T> T getNode(final String xpathExp, final Node node) throws XPathExpressionException {
        return (T) xpath.compile(xpathExp).evaluate(node, XPathConstants.NODE);
    }
}

