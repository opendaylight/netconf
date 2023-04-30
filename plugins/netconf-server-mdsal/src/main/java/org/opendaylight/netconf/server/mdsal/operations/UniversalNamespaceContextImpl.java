/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class UniversalNamespaceContextImpl implements NamespaceContext {
    private static final String DEFAULT_NS = "DEFAULT";
    private final Map<String, String> prefix2Uri = new HashMap<>();
    private final Map<String, String> uri2Prefix = new HashMap<>();

    /**
     * This constructor parses the document and stores all namespaces it can
     * find. If toplevelOnly is true, only namespaces in the root are used.
     *
     * @param document     source document
     * @param toplevelOnly restriction of the search to enhance performance
     */
    public UniversalNamespaceContextImpl(final Document document, final boolean toplevelOnly) {
        readNode(document.getFirstChild(), toplevelOnly);
    }

    /**
     * A single node is read, the namespace attributes are extracted and stored.
     *
     * @param node            to examine
     * @param attributesOnly  if true no recursion happens
     */
    private void readNode(final Node node, final boolean attributesOnly) {
        final NamedNodeMap attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Node attribute = attributes.item(i);
            storeAttr((Attr) attribute);
        }

        if (!attributesOnly) {
            final NodeList chields = node.getChildNodes();
            for (int i = 0; i < chields.getLength(); i++) {
                final Node chield = chields.item(i);
                if (chield.getNodeType() == Node.ELEMENT_NODE) {
                    readNode(chield, false);
                }
            }
        }
    }

    /**
     * This method looks at an attribute and stores it, if it is a namespace
     * attribute.
     *
     * @param attribute to examine
     */
    private void storeAttr(final Attr attribute) {
        // examine the attributes in namespace xmlns
        if (attribute.getNamespaceURI() != null
                && attribute.getNamespaceURI().equals(
                XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            // Default namespace xmlns="uri goes here"
            if (attribute.getNodeName().equals(XMLConstants.XMLNS_ATTRIBUTE)) {
                putInCache(DEFAULT_NS, attribute.getNodeValue());
            } else {
                // The defined prefixes are stored here
                putInCache(attribute.getLocalName(), attribute.getNodeValue());
            }
        }

    }

    private void putInCache(final String prefix, final String uri) {
        prefix2Uri.put(prefix, uri);
        uri2Prefix.put(uri, prefix);
    }

    /**
     * This method is called by XPath. It returns the default namespace, if the
     * prefix is null or "".
     *
     * @param prefix to search for
     * @return uri
     */
    @Override
    public String getNamespaceURI(final String prefix) {
        if (prefix == null || prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
            return prefix2Uri.get(DEFAULT_NS);
        } else {
            return prefix2Uri.get(prefix);
        }
    }

    /**
     * This method is not needed in this context, but can be implemented in a
     * similar way.
     */
    @Override
    public String getPrefix(final String namespaceURI) {
        return uri2Prefix.get(namespaceURI);
    }

    @Override
    public Iterator<String> getPrefixes(final String namespaceURI) {
        // Not implemented
        return null;
    }

}