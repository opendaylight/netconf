/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.RestconfNormalizedNodeWriter;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.xml.sax.SAXException;

/**
 * Normalized node writer for XML.
 *
 * @deprecated This class will be replaced by NormalizedNodeXmlBodyWriter from restconf-nb-rfc8040
 */
@Deprecated
@Provider
@Produces({
    Draft02.MediaTypes.API + RestconfService.XML,
    Draft02.MediaTypes.DATA + RestconfService.XML,
    Draft02.MediaTypes.OPERATION + RestconfService.XML,
    MediaType.APPLICATION_XML,
    MediaType.TEXT_XML
})
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
        final InstanceIdentifierContext pathContext = context.getInstanceIdentifierContext();
        if (context.getData() == null) {
            return;
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

        writeNormalizedNode(xmlWriter, pathContext.inference().toSchemaInferenceStack(), pathContext, data,
            context.getWriterParameters().getDepth());
    }

    private static void writeNormalizedNode(final XMLStreamWriter xmlWriter, final SchemaInferenceStack stack,
            final InstanceIdentifierContext pathContext, NormalizedNode data, final @Nullable Integer depth)
            throws IOException {
        final RestconfNormalizedNodeWriter nnWriter;
        final EffectiveModelContext schemaCtx = pathContext.getSchemaContext();
        if (stack.isEmpty()) {
            nnWriter = createNormalizedNodeWriter(xmlWriter, pathContext.inference(), depth);
            if (data instanceof DOMSourceAnyxmlNode) {
                try {
                    writeElements(xmlWriter, nnWriter,
                            (ContainerNode) NetconfUtil.transformDOMSourceToNormalizedNode(schemaCtx,
                                    ((DOMSourceAnyxmlNode)data).body()).getResult());
                } catch (XMLStreamException | URISyntaxException | SAXException e) {
                    throw new IOException("Cannot write anyxml", e);
                }
            } else {
                writeElements(xmlWriter, nnWriter, (ContainerNode) data);
            }
        }  else if (pathContext.getSchemaNode() instanceof RpcDefinition) {
            final var rpc = (RpcDefinition) pathContext.getSchemaNode();
            final var tmp = SchemaInferenceStack.of(pathContext.getSchemaContext());
            tmp.enterSchemaTree(rpc.getQName());
            tmp.enterSchemaTree(rpc.getOutput().getQName());

            nnWriter = createNormalizedNodeWriter(xmlWriter, tmp.toInference(), depth);
            writeElements(xmlWriter, nnWriter, (ContainerNode) data);
        } else {
            stack.exit();
            nnWriter = createNormalizedNodeWriter(xmlWriter, stack.toInference(), depth);
            if (data instanceof MapEntryNode) {
                // Restconf allows returning one list item. We need to wrap it
                // in map node in order to serialize it properly
                data = ImmutableNodes.mapNodeBuilder(data.getIdentifier().getNodeType())
                    .addChild((MapEntryNode) data)
                    .build();
            }
            nnWriter.write(data);
        }
        nnWriter.flush();
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(final XMLStreamWriter xmlWriter,
            final Inference inference, final @Nullable Integer depth) {
        final NormalizedNodeStreamWriter xmlStreamWriter =
            XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, inference);
        if (depth != null) {
            return DepthAwareNormalizedNodeWriter.forStreamWriter(xmlStreamWriter, depth);
        }

        return RestconfDelegatingNormalizedNodeWriter.forStreamWriter(xmlStreamWriter);
    }

    private static void writeElements(final XMLStreamWriter xmlWriter, final RestconfNormalizedNodeWriter nnWriter,
            final ContainerNode data) throws IOException {
        final QName name = data.getIdentifier().getNodeType();
        try {
            xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, name.getLocalName(),
                    name.getNamespace().toString());
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
