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
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.rfc7952.data.api.NormalizedMetadata;
import org.opendaylight.yangtools.rfc7952.data.util.NormalizedMetadataWriter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

    /**
     * Create an {@link AnyXmlNode} containing the content of a {@link NormalizedNode} structure.
     *
     * @param document W3C DOM document to use for creating nodes
     * @param nodeId NodeIdentifier of resulting AnyXmlNode
     * @param data Data to write
     * @param context SchemaContext to use for encoding
     * @param schemaPath Schema root path
     * @throws IOException when the writing process fails
     * @throws NullPointerException if any of the arguments is null
     */
    @Beta
    public static @NonNull AnyXmlNode createAnyxmlNode(final Document document, final NodeIdentifier nodeId,
            final NormalizedNode<?, ?> data, final SchemaContext context, final SchemaPath schemaPath)
                    throws IOException {
        return createElement(document, nodeId, data, context, schemaPath, null);
    }

    /**
     * Create an {@link AnyXmlNode} containing the content of a {@link NormalizedNode} structure.
     *
     * @param document W3C DOM document to use for creating nodes
     * @param nodeId NodeIdentifier of resulting AnyXmlNode
     * @param data Data to write
     * @param context SchemaContext to use for encoding
     * @param schemaPath Schema root path
     * @throws IOException when the writing process fails
     * @throws NullPointerException if any of the arguments is null
     */
    @Beta
    public static @NonNull AnyXmlNode createAnyxmlNode(final Document document, final NodeIdentifier nodeId,
            final NormalizedNode<?, ?> data, final SchemaContext context, final SchemaPath schemaPath,
            final AnyXmlNamespaceContext namespaceContext) throws IOException {
        return createElement(document, nodeId, data, context, schemaPath, requireNonNull(namespaceContext));
    }

    /**
     * Write the content of a {@link NormalizedNode} structure into a W3C DOM {@link Node}. This process creates new
     * nodes underneath the target node -- which can be either a {@link Document} or a Node itself.
     *
     * @param output Target node
     * @param data Data to write
     * @param context SchemaContext to use for encoding
     * @param schemaPath Schema root path
     * @throws IOException when the writing process fails
     * @throws NullPointerException if any of the arguments is null
     */
    @Beta
    public static void writeNormalizedNode(final Node output, final NormalizedNode<?, ?> data,
            final SchemaContext context, final SchemaPath schemaPath) throws IOException {
        final DOMResult result = new DOMResult(requireNonNull(output));
        try {
            XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(result);
            try {
                writeNormalizedNode(writer, data, context, schemaPath);
            } finally {
                writer.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write node", e);
        }
    }

    private static void writeNormalizedNode(final XMLStreamWriter writer, final NormalizedNode<?, ?> normalized,
            final SchemaContext context, final SchemaPath schemaPath) throws IOException {
        try (
                NormalizedNodeStreamWriter normalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(
                    writer, context, schemaPath);
                NormalizedNodeWriter normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(
                    normalizedNodeStreamWriter)) {
            normalizedNodeWriter.write(normalized);
            normalizedNodeWriter.flush();
        }
    }

    private static @NonNull AnyXmlNode createElement(final Document document, final NodeIdentifier nodeId,
            final NormalizedNode<?, ?> data, final SchemaContext context, final SchemaPath schemaPath,
            final @Nullable AnyXmlNamespaceContext namespaceContext) throws IOException {
        final QName qname = nodeId.getNodeType();
        final String elementNs = qname.getNamespace().toString();
        final Element element = document.createElementNS(elementNs, qname.getLocalName());
        element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE, elementNs);

        final DOMResult result = new DOMResult(element);
        try {
            final XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(result);
            try {
                writer.setDefaultNamespace(elementNs);
                if (namespaceContext != null) {
                    applyNamespaceContext(element, writer, namespaceContext);
                }

                writeNormalizedNode(writer, data, context, schemaPath);
            } finally {
                writer.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write node", e);
        }

        return Builders.anyXmlBuilder().withNodeIdentifier(nodeId).withValue(new DOMSource(element)).build();
    }

    private static void applyNamespaceContext(final Element element, final XMLStreamWriter writer,
            final AnyXmlNamespaceContext namespaceContext) throws XMLStreamException {
        for (Entry<String, String> entry : namespaceContext.uriPrefixEntries()) {
            final String namespaceURI = entry.getKey();
            final String prefix = entry.getValue();
            checkArgument(!Strings.isNullOrEmpty(prefix));
            element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE + ':' + prefix,
                namespaceURI);
            writer.setPrefix(prefix, namespaceURI);
        }

        try {
            writer.setNamespaceContext(namespaceContext);
        } catch (UnsupportedOperationException e) {
            // Java DOM writer is helpful this way
            LOG.debug("Initial namespace context not set", e);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void writeNormalizedNode(final NormalizedNode<?, ?> normalized,
                                           final @Nullable NormalizedMetadata metadata,
                                           final DOMResult result, final SchemaPath schemaPath,
                                           final SchemaContext context) throws IOException, XMLStreamException {
        if (metadata == null) {
            writeNormalizedNode(normalized, result, schemaPath, context);
            return;
        }

        final XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(result);
        try (
             NormalizedNodeStreamWriter normalizedNodeStreamWriter =
                     XMLStreamNormalizedNodeStreamWriter.create(writer, context, schemaPath);
                NormalizedMetadataWriter normalizedNodeWriter =
                     NormalizedMetadataWriter.forStreamWriter(normalizedNodeStreamWriter)
        ) {
            normalizedNodeWriter.write(normalized, metadata);
            normalizedNodeWriter.flush();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (final Exception e) {
                LOG.warn("Unable to close resource properly", e);
            }
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
