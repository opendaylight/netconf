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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javanet.staxutils.IndentingXMLStreamWriter;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import javax.xml.XMLConstants;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.DepthParameter;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

@Provider
@Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
public class XmlNormalizedNodeBodyWriter extends AbstractNormalizedNodeBodyWriter {
    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    @Override
    public void writeTo(final NormalizedNodePayload context,
                        final Class<?> type,
                        final Type genericType,
                        final Annotation[] annotations,
                        final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws IOException, WebApplicationException {
        final InstanceIdentifierContext<?> pathContext = context.getInstanceIdentifierContext();
        if (context.getData() == null) {
            return;
        }
        if (httpHeaders != null) {
            for (final Map.Entry<String, Object> entry : context.getNewHeaders().entrySet()) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        XMLStreamWriter xmlWriter;
        try {
            xmlWriter = XML_FACTORY.createXMLStreamWriter(entityStream, StandardCharsets.UTF_8.name());
            if (context.getWriterParameters().isPrettyPrint()) {
                xmlWriter = new IndentingXMLStreamWriter(xmlWriter);
            }
        } catch (final XMLStreamException | FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }
        final NormalizedNode data = context.getData();
        final SchemaPath schemaPath = pathContext.getSchemaNode().getPath();

        writeNormalizedNode(xmlWriter, schemaPath, pathContext, data, context.getWriterParameters().getDepth(),
                context.getWriterParameters().getFields());
    }

    private static void writeNormalizedNode(final XMLStreamWriter xmlWriter, final SchemaPath path,
            final InstanceIdentifierContext<?> pathContext, final NormalizedNode data, final DepthParameter depth,
            final List<Set<QName>> fields) throws IOException {
        final RestconfNormalizedNodeWriter nnWriter;
        final EffectiveModelContext schemaCtx = pathContext.getSchemaContext();

        if (pathContext.getSchemaNode() instanceof RpcDefinition) {
            /*
             *  RpcDefinition is not supported as initial codec in XMLStreamWriter,
             *  so we need to emit initial output declaration..
             */
            nnWriter = createNormalizedNodeWriter(
                    xmlWriter,
                    schemaCtx,
                    ((RpcDefinition) pathContext.getSchemaNode()).getOutput().getPath(),
                    depth,
                    fields);
            writeElements(xmlWriter, nnWriter, (ContainerNode) data);
        } else if (pathContext.getSchemaNode() instanceof ActionDefinition) {
            /*
             *  ActionDefinition is not supported as initial codec in XMLStreamWriter,
             *  so we need to emit initial output declaration..
             */
            nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx,
                ((ActionDefinition) pathContext.getSchemaNode()).getOutput().getPath(), depth, fields);
            writeElements(xmlWriter, nnWriter, (ContainerNode) data);
        } else {
            if (SchemaPath.ROOT.equals(path)) {
                nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx, path, depth, fields);
            } else {
                nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx, path.getParent(), depth, fields);
            }

            if (data instanceof MapEntryNode) {
                // Restconf allows returning one list item. We need to wrap it
                // in map node in order to serialize it properly
                nnWriter.write(ImmutableNodes.mapNodeBuilder(data.getIdentifier().getNodeType())
                    .addChild((MapEntryNode) data)
                    .build());
            } else {
                nnWriter.write(data);
            }
        }

        nnWriter.flush();
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(final XMLStreamWriter xmlWriter,
            final EffectiveModelContext schemaContext, final SchemaPath schemaPath, final DepthParameter depth,
            final List<Set<QName>> fields) {
        final NormalizedNodeStreamWriter xmlStreamWriter = XMLStreamNormalizedNodeStreamWriter
                .create(xmlWriter, schemaContext, schemaPath);
        return ParameterAwareNormalizedNodeWriter.forStreamWriter(xmlStreamWriter, depth, fields);
    }

    private static void writeElements(final XMLStreamWriter xmlWriter, final RestconfNormalizedNodeWriter nnWriter,
            final ContainerNode data) throws IOException {
        final QName name = data.getIdentifier().getNodeType();
        try {
            xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX,
                    name.getLocalName(), name.getNamespace().toString());
            xmlWriter.writeDefaultNamespace(name.getNamespace().toString());
            for (final NormalizedNode child : data.body()) {
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
