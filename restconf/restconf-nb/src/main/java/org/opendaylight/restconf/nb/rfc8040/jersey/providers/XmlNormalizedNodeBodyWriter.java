/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javanet.staxutils.IndentingXMLStreamWriter;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import javax.xml.XMLConstants;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

@Provider
@Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
public final class XmlNormalizedNodeBodyWriter extends AbstractNormalizedNodeBodyWriter {
    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    @Override
    void writeOperationOutput(final SchemaInferenceStack stack, final QueryParameters writerParameters,
            final ContainerNode output, final OutputStream entityStream) throws IOException {
        // RpcDefinition/ActionDefinition is not supported as initial codec in XMLStreamWriter, so we need to emit
        // initial output declaration.
        final var xmlWriter = createXmlWriter(entityStream, writerParameters.prettyPrint());
        final var nnWriter = createNormalizedNodeWriter(xmlWriter, stack.toInference(), writerParameters);
        writeElements(xmlWriter, nnWriter, output);
        nnWriter.flush();
    }

    @Override
    void writeData(final SchemaInferenceStack stack, final QueryParameters writerParameters, final NormalizedNode data,
            final OutputStream entityStream) throws IOException {
        final boolean isRoot;
        if (!stack.isEmpty()) {
            stack.exit();
            isRoot = false;
        } else {
            isRoot = true;
        }

        final var xmlWriter = createXmlWriter(entityStream, writerParameters.prettyPrint());
        final var nnWriter = createNormalizedNodeWriter(xmlWriter, stack.toInference(), writerParameters);
        if (data instanceof MapEntryNode mapEntry) {
            // Restconf allows returning one list item. We need to wrap it
            // in map node in order to serialize it properly
            nnWriter.write(ImmutableNodes.mapNodeBuilder(data.name().getNodeType()).addChild(mapEntry).build());
        } else if (isRoot) {
            if (data instanceof ContainerNode container && container.isEmpty()) {
                writeEmptyDataNode(xmlWriter, container);
            } else {
                writeAndWrapInDataNode(xmlWriter, nnWriter, data);
            }
        } else {
            nnWriter.write(data);
        }
        nnWriter.flush();
    }

    private static XMLStreamWriter createXmlWriter(final OutputStream entityStream,
            final @Nullable PrettyPrintParam prettyPrint) {
        final XMLStreamWriter xmlWriter;
        try {
            xmlWriter = XML_FACTORY.createXMLStreamWriter(entityStream, StandardCharsets.UTF_8.name());
        } catch (XMLStreamException | FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }

        return prettyPrint != null && prettyPrint.value() ? new IndentingXMLStreamWriter(xmlWriter) : xmlWriter;
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(final XMLStreamWriter xmlWriter,
            final Inference inference, final QueryParameters writerParameters) {
        return ParameterAwareNormalizedNodeWriter.forStreamWriter(
            XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, inference),
            writerParameters.depth(), writerParameters.fields());
    }

    private static void writeAndWrapInDataNode(final XMLStreamWriter xmlWriter,
            final RestconfNormalizedNodeWriter nnWriter, final NormalizedNode data) throws IOException {
        final QName nodeType = data.name().getNodeType();
        final String namespace = nodeType.getNamespace().toString();
        try {
            xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, nodeType.getLocalName(), namespace);
            xmlWriter.writeDefaultNamespace(namespace);
            nnWriter.write(data);
            xmlWriter.writeEndElement();
            xmlWriter.flush();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write elements", e);
        }
    }

    private static void writeEmptyDataNode(final XMLStreamWriter xmlWriter, final ContainerNode data)
            throws IOException {
        final QName nodeType = data.name().getNodeType();
        final String namespace = nodeType.getNamespace().toString();
        try {
            xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, nodeType.getLocalName(), namespace);
            xmlWriter.writeDefaultNamespace(namespace);
            xmlWriter.writeEndElement();
            xmlWriter.flush();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write elements", e);
        }
    }

    private static void writeElements(final XMLStreamWriter xmlWriter, final RestconfNormalizedNodeWriter nnWriter,
            final ContainerNode data) throws IOException {
        final QName nodeType = data.name().getNodeType();
        final String namespace = nodeType.getNamespace().toString();
        try {
            xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, nodeType.getLocalName(), namespace);
            xmlWriter.writeDefaultNamespace(namespace);
            for (var child : data.body()) {
                nnWriter.write(child);
            }
            nnWriter.flush();
            xmlWriter.writeEndElement();
            xmlWriter.flush();
        } catch (final XMLStreamException e) {
            throw new IOException("Failed to write elements", e);
        }
    }
}
