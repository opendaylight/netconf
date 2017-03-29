/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Produces("application/yang.data+xml")
public class XmlWriter implements MessageBodyWriter<RequestContext> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlWriter.class);

    public static final XMLOutputFactory XML_FACTORY;
    private final SchemaContext schemaContext;

    public XmlWriter(final SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    @Override
    public boolean isWriteable(final Class<?> aClass, final Type type, final Annotation[] annotations, final MediaType mediaType) {
        //TODO implement check
        return true;
    }

    @Override
    public long getSize(final RequestContext normalizedNode, final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(final RequestContext context, final Class<?> type, final Type genericType,
                        final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream)
            throws IOException {

        final XMLStreamWriter xmlWriter;
        try {
            xmlWriter = XML_FACTORY.createXMLStreamWriter(entityStream);
        } catch (final XMLStreamException e) {
            LOG.error("Error creating XML stream writer", e);
            throw new IllegalStateException(e);
        }
        try (final NormalizedNodeStreamWriter normalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                schemaContext, getParentSchemaPath(context.getPath()));
             final NormalizedNodeWriter normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter)) {
            normalizedNodeWriter.write(context.getData());
            normalizedNodeWriter.flush();
        } catch (final IOException e) {
            LOG.error("Error writing a normalized node data", e);
        } finally {
            if (xmlWriter != null) {
                try {
                    xmlWriter.close();
                } catch (final XMLStreamException e) {
                    LOG.error("Error closing a XML stream Writer", e);
                }
            }
        }
    }

    private SchemaPath getParentSchemaPath(final YangInstanceIdentifier path) {
        if (path.isEmpty()) {
            return SchemaPath.ROOT;
        }
        final DataSchemaContextNode<?> child = DataSchemaContextTree.from(schemaContext).getChild(path);
        final DataSchemaNode dataSchemaNode = child.getDataSchemaNode();
        if (dataSchemaNode == null) {
            throw new IllegalStateException("Data schema node for " + path + " not found.");
        }
        if (child.isKeyedEntry()) {
            return child.getDataSchemaNode().getPath();
        }
        return dataSchemaNode.getPath().getParent();
    }

}
