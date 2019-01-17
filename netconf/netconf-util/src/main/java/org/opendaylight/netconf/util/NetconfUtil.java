/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class NetconfUtil {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfUtil.class);
    public static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);
    }

    private NetconfUtil() {}

    public static Document checkIsMessageOk(final Document response) throws DocumentedException {
        XmlElement element = XmlElement.fromDomDocument(response);
        checkState(element.getName().equals(XmlNetconfConstants.RPC_REPLY_KEY));
        element = element.getOnlyChildElement();
        if (element.getName().equals(XmlNetconfConstants.OK)) {
            return response;
        }
        LOG.warn("Can not load last configuration. Operation failed.");
        throw new IllegalStateException("Can not load last configuration. Operation failed: "
                + XmlUtil.toString(response));
    }

    public static void writeNormalizedNode(final Document output, final NormalizedNode<?, ?> normalized,
            final SchemaContext context, final SchemaPath schemaPath) throws IOException, XMLStreamException {
        final DOMResult result = new DOMResult(output);
        final XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(result);
        try {
            writeNormalizedNode(writer, normalized, context, schemaPath);
        } finally {
            writer.close();
        }
    }

    public static Element writeNormalizedNode(final Document document, final QName qname,
            final NormalizedNode<?, ?> normalized, final SchemaContext context, final SchemaPath schemaPath)
                    throws IOException, XMLStreamException {
        return writeNormalizedNode(document, qname, normalized, context, schemaPath, ImmutableMap.of());
    }

    public static Element writeNormalizedNode(final Document document, final QName qname,
            final NormalizedNode<?, ?> normalized, final SchemaContext context, final SchemaPath schemaPath,
            final Map<String, String> additionalNamespaces) throws IOException, XMLStreamException {
        final String elementNs = qname.getNamespace().toString();
        final Element element = document.createElementNS(elementNs, qname.getLocalName());
        element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE, elementNs);

        final DOMResult result = new DOMResult(element);
        final XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(result);
        try {
            writer.setDefaultNamespace(elementNs);

            for (Entry<String, String> entry : additionalNamespaces.entrySet()) {
                final String prefix = entry.getKey();
                final String namespaceURI = entry.getValue();
                checkArgument(!Strings.isNullOrEmpty(prefix));
                element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE + ':' + prefix,
                    namespaceURI);
                writer.setPrefix(prefix, namespaceURI);
            }

            // FIXME: also set initial namespace context

            writeNormalizedNode(writer, normalized, context, schemaPath);
        } finally {
            writer.close();
        }

        return element;
    }

    private static void writeNormalizedNode(final XMLStreamWriter writer, final NormalizedNode<?, ?> normalized,
            final SchemaContext context, final SchemaPath schemaPath) throws IOException {
        try (
                NormalizedNodeStreamWriter normalizedNodeStreamWriter =
                        XMLStreamNormalizedNodeStreamWriter.create(writer, context, schemaPath);
                NormalizedNodeWriter normalizedNodeWriter =
                        NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter)
           ) {
               normalizedNodeWriter.write(normalized);
               normalizedNodeWriter.flush();
        }
    }

    public static void writeFilter(final YangInstanceIdentifier query, final DOMResult result,
            final SchemaPath schemaPath, final SchemaContext context) throws IOException, XMLStreamException {
        if (query.isEmpty()) {
            // No query at all
            return;
        }

        final XMLStreamWriter xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        try {
            try (NormalizedNodeStreamWriter writer =
                    XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, context, schemaPath)) {
                final Iterator<PathArgument> it = query.getPathArguments().iterator();
                final PathArgument first = it.next();
                StreamingContext.fromSchemaAndQNameChecked(context, first.getNodeType()).streamToWriter(writer, first,
                    it);
            }
        } finally {
            xmlWriter.close();
        }
    }
}
