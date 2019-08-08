/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * XML stream-writer with disabled leaf-type validation for specified QName.
 */
final class XmlStreamWriterWithDisabledValidation extends StreamWriterWithDisabledValidation {

    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    private final XMLStreamWriter xmlWriter;
    private final NormalizedNodeStreamWriter xmlNodeStreamWriter;

    /**
     * Creation of the custom XML stream-writer.
     *
     * @param excludedQName        QName of the element that is excluded from type-check.
     * @param outputStream         Output stream that is used for creation of JSON writers.
     * @param schemaPath           Schema-path of the {@link NormalizedNode} to be written.
     * @param schemaContextHandler Handler that holds actual schema context.
     */
    XmlStreamWriterWithDisabledValidation(final QName excludedQName, final OutputStream outputStream,
            final SchemaPath schemaPath, final SchemaContextHandler schemaContextHandler) {
        super(excludedQName);
        try {
            this.xmlWriter = XML_FACTORY.createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name());
        } catch (final XMLStreamException | FactoryConfigurationError e) {
            throw new IllegalStateException("Cannot create XML writer", e);
        }
        this.xmlNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                schemaContextHandler.get(), schemaPath);
    }

    @Override
    protected NormalizedNodeStreamWriter delegate() {
        return xmlNodeStreamWriter;
    }

    @Override
    void startLeafNodeWithDisabledValidation(final NodeIdentifier nodeIdentifier) throws IOException {
        final String namespace = nodeIdentifier.getNodeType().getNamespace().toString();
        try {
            xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX,
                    nodeIdentifier.getNodeType().getLocalName(), namespace);
        } catch (XMLStreamException e) {
            throw new IOException("Error writing leaf node", e);
        }
    }

    @Override
    void scalarValueWithDisabledValidation(final Object value) throws IOException {
        try {
            xmlWriter.writeCharacters(value.toString());
        } catch (XMLStreamException e) {
            throw new IOException("Error writing value", e);
        }
    }

    @Override
    void endNodeWithDisabledValidation() throws IOException {
        try {
            xmlWriter.writeEndElement();
        } catch (XMLStreamException e) {
            throw new IOException("Error writing end-node", e);
        }
    }

    @Override
    public void close() throws IOException {
        xmlNodeStreamWriter.close();
        try {
            xmlWriter.close();
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }
}