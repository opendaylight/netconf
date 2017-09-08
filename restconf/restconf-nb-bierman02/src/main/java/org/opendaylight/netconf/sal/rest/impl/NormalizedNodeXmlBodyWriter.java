/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import javanet.staxutils.IndentingXMLStreamWriter;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.XMLConstants;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.RestconfNormalizedNodeWriter;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Normalized node writer for XML.
 *
 * @deprecated This class will be replaced by NormalizedNodeXmlBodyWriter from restconf-nb-rfc8040
 */
@Deprecated
@Provider
@Produces({ Draft02.MediaTypes.API + RestconfService.XML, Draft02.MediaTypes.DATA + RestconfService.XML,
        Draft02.MediaTypes.OPERATION + RestconfService.XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
public class NormalizedNodeXmlBodyWriter implements MessageBodyWriter<NormalizedNodeContext> {

    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return type.equals(NormalizedNodeContext.class);
    }

    @Override
    public long getSize(final NormalizedNodeContext context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(final NormalizedNodeContext context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType,
            final MultivaluedMap<String, Object> httpHeaders, final OutputStream entityStream) throws IOException,
            WebApplicationException {
        for (final Entry<String, Object> entry : context.getNewHeaders().entrySet()) {
            httpHeaders.add(entry.getKey(), entry.getValue());
        }
        final InstanceIdentifierContext<?> pathContext = context.getInstanceIdentifierContext();
        if (context.getData() == null) {
            return;
        }

        XMLStreamWriter xmlWriter;
        try {
            xmlWriter = XML_FACTORY.createXMLStreamWriter(entityStream, StandardCharsets.UTF_8.name());
            if (context.getWriterParameters().isPrettyPrint()) {
                xmlWriter = new IndentingXMLStreamWriter(xmlWriter);
            }
        } catch (final XMLStreamException e) {
            throw new IllegalStateException(e);
        } catch (final FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }
        final NormalizedNode<?, ?> data = context.getData();
        final SchemaPath schemaPath = pathContext.getSchemaNode().getPath();

        writeNormalizedNode(xmlWriter, schemaPath, pathContext, data,
                Optional.fromNullable(context.getWriterParameters().getDepth()));
    }

    private static void writeNormalizedNode(final XMLStreamWriter xmlWriter, final SchemaPath schemaPath,
            final InstanceIdentifierContext<?> pathContext, NormalizedNode<?, ?> data, final Optional<Integer> depth)
            throws IOException {
        final RestconfNormalizedNodeWriter nnWriter;
        final SchemaContext schemaCtx = pathContext.getSchemaContext();
        if (SchemaPath.ROOT.equals(schemaPath)) {
            nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx, schemaPath, depth);
            writeElements(xmlWriter, nnWriter, (ContainerNode) data);
        }  else if (pathContext.getSchemaNode() instanceof RpcDefinition) {
            nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx,
                    ((RpcDefinition) pathContext.getSchemaNode()).getOutput().getPath(), depth);
            writeElements(xmlWriter, nnWriter, (ContainerNode) data);
        } else {
            nnWriter = createNormalizedNodeWriter(xmlWriter, schemaCtx, schemaPath.getParent(), depth);
            if (data instanceof MapEntryNode) {
                // Restconf allows returning one list item. We need to wrap it
                // in map node in order to serialize it properly
                data = ImmutableNodes.mapNodeBuilder(data.getNodeType()).addChild((MapEntryNode) data).build();
            }
            nnWriter.write(data);
        }
        nnWriter.flush();
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(final XMLStreamWriter xmlWriter,
            final SchemaContext schemaContext, final SchemaPath schemaPath, final Optional<Integer> depth) {
        final NormalizedNodeStreamWriter xmlStreamWriter =
                XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, schemaContext, schemaPath);
        if (depth.isPresent()) {
            return DepthAwareNormalizedNodeWriter.forStreamWriter(xmlStreamWriter, depth.get());
        }

        return RestconfDelegatingNormalizedNodeWriter.forStreamWriter(xmlStreamWriter);
    }

    private static void writeElements(final XMLStreamWriter xmlWriter, final RestconfNormalizedNodeWriter nnWriter,
                               final ContainerNode data)
            throws IOException {
        try {
            final QName name = data.getNodeType();
            xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, name.getLocalName(),
                    name.getNamespace().toString());
            xmlWriter.writeDefaultNamespace(name.getNamespace().toString());
            for (final NormalizedNode<?,?> child : data.getValue()) {
                nnWriter.write(child);
            }
            nnWriter.flush();
            xmlWriter.writeEndElement();
            xmlWriter.flush();
        } catch (final XMLStreamException e) {
            Throwables.propagate(e);
        }
    }
}
