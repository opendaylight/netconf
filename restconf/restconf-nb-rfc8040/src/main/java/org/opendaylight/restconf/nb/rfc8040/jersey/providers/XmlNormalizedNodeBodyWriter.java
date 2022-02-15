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
import java.util.stream.Collectors;
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
import org.opendaylight.restconf.nb.rfc8040.DepthParam;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

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

            final var prettyPrint = context.getWriterParameters().prettyPrint();
            if (prettyPrint != null && prettyPrint.value()) {
                xmlWriter = new IndentingXMLStreamWriter(xmlWriter);
            }
        } catch (final XMLStreamException | FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }
        final NormalizedNode data = context.getData();

        writeNormalizedNode(xmlWriter, pathContext, data, context.getWriterParameters().depth(),
                context.getWriterParameters().fields());
    }

    private static void writeNormalizedNode(final XMLStreamWriter xmlWriter,
            final InstanceIdentifierContext<?> pathContext, final NormalizedNode data, final DepthParam depth,
            final List<Set<QName>> fields) throws IOException {
        final RestconfNormalizedNodeWriter nnWriter;
        final EffectiveModelContext schemaCtx = pathContext.getSchemaContext();

        if (pathContext.getSchemaNode() instanceof RpcDefinition) {
            /*
             *  RpcDefinition is not supported as initial codec in XMLStreamWriter,
             *  so we need to emit initial output declaration..
             */
            final RpcDefinition rpc = (RpcDefinition) pathContext.getSchemaNode();
            final SchemaPath rpcPath = SchemaPath.of(Absolute.of(rpc.getQName(), rpc.getOutput().getQName()));
            nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx, rpcPath, depth, fields);
            writeElements(xmlWriter, nnWriter, (ContainerNode) data);
        } else if (pathContext.getSchemaNode() instanceof ActionDefinition) {
            /*
             *  ActionDefinition is not supported as initial codec in XMLStreamWriter,
             *  so we need to emit initial output declaration..
             */
            final ActionDefinition actDef = (ActionDefinition) pathContext.getSchemaNode();
            final List<QName> qNames = pathContext.getInstanceIdentifier().getPathArguments().stream()
                    .filter(arg -> !(arg instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates))
                    .filter(arg -> !(arg instanceof YangInstanceIdentifier.AugmentationIdentifier))
                    .map(PathArgument::getNodeType)
                    .collect(Collectors.toList());
            qNames.add(actDef.getQName());
            qNames.add(actDef.getOutput().getQName());
            final SchemaPath actPath = SchemaPath.of(Absolute.of(qNames));
            nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx, actPath, depth, fields);
            writeElements(xmlWriter, nnWriter, (ContainerNode) data);
        } else {
            final boolean isRoot = pathContext.getInstanceIdentifier().isEmpty();
            if (isRoot) {
                nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx, SchemaPath.ROOT, depth, fields);
            } else {
                final List<QName> qNames = pathContext.getInstanceIdentifier().getPathArguments().stream()
                        .filter(arg -> !(arg instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates))
                        .filter(arg -> !(arg instanceof YangInstanceIdentifier.AugmentationIdentifier))
                        .map(PathArgument::getNodeType)
                        .collect(Collectors.toList());
                final SchemaPath path = SchemaPath.of(Absolute.of(qNames));
                nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx, path.getParent(), depth, fields);
            }

            if (data instanceof MapEntryNode) {
                // Restconf allows returning one list item. We need to wrap it
                // in map node in order to serialize it properly
                nnWriter.write(ImmutableNodes.mapNodeBuilder(data.getIdentifier().getNodeType())
                    .addChild((MapEntryNode) data)
                    .build());
            } else if (isRoot) {
                if (data instanceof ContainerNode && ((ContainerNode) data).isEmpty()) {
                    writeEmptyDataNode(xmlWriter, data);
                } else {
                    writeAndWrapInDataNode(xmlWriter, nnWriter, data);
                }
            } else {
                nnWriter.write(data);
            }
        }

        nnWriter.flush();
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(final XMLStreamWriter xmlWriter,
            final EffectiveModelContext schemaContext, final SchemaPath schemaPath, final DepthParam depth,
            final List<Set<QName>> fields) {
        return ParameterAwareNormalizedNodeWriter.forStreamWriter(
            XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, schemaContext, schemaPath), depth, fields);
    }

    private static void writeAndWrapInDataNode(final XMLStreamWriter xmlWriter,
            final RestconfNormalizedNodeWriter nnWriter, final NormalizedNode data) throws IOException {
        final QName nodeType = data.getIdentifier().getNodeType();
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

    private static void writeEmptyDataNode(final XMLStreamWriter xmlWriter, final NormalizedNode data)
            throws IOException {
        final QName nodeType = data.getIdentifier().getNodeType();
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
        final QName nodeType = data.getIdentifier().getNodeType();
        final String namespace = nodeType.getNamespace().toString();
        try {
            xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, nodeType.getLocalName(), namespace);
            xmlWriter.writeDefaultNamespace(namespace);
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
