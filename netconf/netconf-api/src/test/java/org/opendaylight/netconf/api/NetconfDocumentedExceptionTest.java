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

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Iterator;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
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
            public Iterator<?> getPrefixes(final String namespaceURI) {
                return Collections.singletonList("netconf").iterator();
            }

            @Override
            public String getPrefix(final String namespaceURI) {
                return "netconf";
            }

            @Override
            public String getNamespaceURI(final String prefix) {
                return XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0;
            }
        });
    }

    @Test
    public void testToAndFromXMLDocument() throws XPathExpressionException {
        final String errorMessage = "mock error message";
        DocumentedException ex = new NetconfDocumentedException(errorMessage, null,
                DocumentedException.ErrorType.PROTOCOL,
                DocumentedException.ErrorTag.DATA_EXISTS,
                DocumentedException.ErrorSeverity.WARNING,
                ImmutableMap.of("foo", "bar"));

        final Document doc = ex.toXMLDocument();
        assertNotNull("Document is null", doc);

        final Node rootNode = doc.getDocumentElement();

        assertEquals("getNamespaceURI", "urn:ietf:params:xml:ns:netconf:base:1.0", rootNode.getNamespaceURI());
        assertEquals("getLocalName", "rpc-reply", rootNode.getLocalName());

        final Node rpcErrorNode = getNode("/netconf:rpc-reply/netconf:rpc-error", rootNode);
        assertNotNull("rpc-error not found", rpcErrorNode);

        final Node errorTypeNode = getNode("netconf:error-type", rpcErrorNode);
        assertNotNull("error-type not found", errorTypeNode);
        assertEquals("error-type", DocumentedException.ErrorType.PROTOCOL.getTypeValue(),
                errorTypeNode.getTextContent());

        final Node errorTagNode = getNode("netconf:error-tag", rpcErrorNode);
        assertNotNull("error-tag not found", errorTagNode);
        assertEquals("error-tag", DocumentedException.ErrorTag.DATA_EXISTS.getTagValue(),
                errorTagNode.getTextContent());

        final Node errorSeverityNode = getNode("netconf:error-severity", rpcErrorNode);
        assertNotNull("error-severity not found", errorSeverityNode);
        assertEquals("error-severity", DocumentedException.ErrorSeverity.WARNING.getSeverityValue(),
                errorSeverityNode.getTextContent());

        final Node errorInfoNode = getNode("netconf:error-info/netconf:foo", rpcErrorNode);
        assertNotNull("foo not found", errorInfoNode);
        assertEquals("foo", "bar", errorInfoNode.getTextContent());

        final Node errorMsgNode = getNode("netconf:error-message", rpcErrorNode);
        assertNotNull("error-message not found", errorMsgNode);
        assertEquals("error-message", errorMessage, errorMsgNode.getTextContent());

        // Test fromXMLDocument

        ex = DocumentedException.fromXMLDocument(doc);

        assertNotNull("NetconfDocumentedException is null", ex);
        assertEquals("getErrorSeverity", DocumentedException.ErrorSeverity.WARNING, ex.getErrorSeverity());
        assertEquals("getErrorTag", DocumentedException.ErrorTag.DATA_EXISTS, ex.getErrorTag());
        assertEquals("getErrorType", DocumentedException.ErrorType.PROTOCOL, ex.getErrorType());
        assertEquals("getLocalizedMessage", errorMessage, ex.getLocalizedMessage());
        assertEquals("getErrorInfo", ImmutableMap.of("foo", "bar"), ex.getErrorInfo());
    }

    @SuppressWarnings("unchecked")
    <T> T getNode(final String xpathExp, final Node node) throws XPathExpressionException {
        return (T) xpath.compile(xpathExp).evaluate(node, XPathConstants.NODE);
    }
}

