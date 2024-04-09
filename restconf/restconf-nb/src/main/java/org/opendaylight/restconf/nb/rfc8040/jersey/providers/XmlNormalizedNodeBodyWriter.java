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
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.WriterParameters;
import org.opendaylight.restconf.server.spi.FormattableBodySupport;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

@Provider
@Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
public final class XmlNormalizedNodeBodyWriter extends AbstractNormalizedNodeBodyWriter {
    @Override
    void writeData(final SchemaInferenceStack stack, final NormalizedNode data, final WriterParameters writerParameters,
            final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
        final boolean isRoot;
        if (!stack.isEmpty()) {
            stack.exit();
            isRoot = false;
        } else {
            isRoot = true;
        }

        final var xmlWriter = FormattableBodySupport.createXmlWriter(out, prettyPrint);
        final var nnWriter = createNormalizedNodeWriter(xmlWriter, stack.toInference(), writerParameters);
        if (data instanceof MapEntryNode mapEntry) {
            // Restconf allows returning one list item. We need to wrap it
            // in map node in order to serialize it properly
            nnWriter.write(ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(data.name().getNodeType()))
                .addChild(mapEntry)
                .build());
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

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(final XMLStreamWriter xmlWriter,
            final Inference inference, final WriterParameters writerParameters) {
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
}
