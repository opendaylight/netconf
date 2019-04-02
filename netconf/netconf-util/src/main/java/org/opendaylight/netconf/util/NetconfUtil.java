/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.Iterator;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.rfc7952.data.api.NormalizedMetadata;
import org.opendaylight.yangtools.rfc7952.data.util.NormalizedMetadataWriter;
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
        Preconditions.checkState(element.getName().equals(XmlNetconfConstants.RPC_REPLY_KEY));
        element = element.getOnlyChildElement();
        if (element.getName().equals(XmlNetconfConstants.OK)) {
            return response;
        }
        LOG.warn("Can not load last configuration. Operation failed.");
        throw new IllegalStateException("Can not load last configuration. Operation failed: "
                + XmlUtil.toString(response));
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void writeNormalizedNode(final NormalizedNode<?, ?> normalized, final DOMResult result,
                                           final SchemaPath schemaPath, final SchemaContext context)
            throws IOException, XMLStreamException {
        final XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(result);
        try (
             NormalizedNodeStreamWriter normalizedNodeStreamWriter =
                     XMLStreamNormalizedNodeStreamWriter.create(writer, context, schemaPath);
             NormalizedNodeWriter normalizedNodeWriter =
                     NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter)
        ) {
            normalizedNodeWriter.write(normalized);
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
