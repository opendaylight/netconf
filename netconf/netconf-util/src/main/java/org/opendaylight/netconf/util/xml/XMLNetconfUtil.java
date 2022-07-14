/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util.xml;

import java.util.Set;
import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

public final class XMLNetconfUtil {
    private static final XPathFactory FACTORY = XPathFactory.newInstance();
    private static final NamespaceContext NS_CONTEXT = new HardcodedNamespaceResolver("netconf",
        XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);

    private XMLNetconfUtil() {
        // Hidden on purpose
    }

    public static XPathExpression compileXPath(final String xpath) {
        final XPath newXPath = FACTORY.newXPath();
        newXPath.setNamespaceContext(NS_CONTEXT);
        try {
            return newXPath.compile(xpath);
        } catch (final XPathExpressionException e) {
            throw new IllegalStateException("Error while compiling xpath expression " + xpath, e);
        }
    }

    public static DOMSource filterDomNamespaces(final DOMSource domSource, final Set<String> namespacesAllowed) {
        final var sourceDoc = XmlUtil.newDocument();
        sourceDoc.appendChild(sourceDoc.importNode(domSource.getNode(), true));

        final var treeWalker = ((DocumentTraversal) sourceDoc).createTreeWalker(sourceDoc.getDocumentElement(),
            NodeFilter.SHOW_ALL, node -> {
                final var namespace = node.getNamespaceURI();
                return namespace == null || namespacesAllowed.contains(node.getNamespaceURI())
                    ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
            }, false);

        final var filteredDoc = XmlUtil.newDocument();
        filteredDoc.appendChild(filteredDoc.importNode(treeWalker.getRoot(), false));
        filterChildren(treeWalker, filteredDoc, filteredDoc.getDocumentElement());

        return new DOMSource(filteredDoc.getDocumentElement());
    }

    private static void filterChildren(final TreeWalker treeWalker, final Document filteredDoc,
            final Node filteredNode) {
        if (treeWalker.firstChild() != null) {
            for (var node = treeWalker.getCurrentNode(); node != null; node = treeWalker.nextSibling()) {
                final var newFilteredNode = filteredDoc.importNode(node, false);
                filteredNode.appendChild(newFilteredNode);
                filterChildren(treeWalker, filteredDoc, newFilteredNode);
                treeWalker.setCurrentNode(node);
            }
        }
    }
}
