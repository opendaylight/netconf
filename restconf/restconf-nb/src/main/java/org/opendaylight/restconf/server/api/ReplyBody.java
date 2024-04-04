/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import javanet.staxutils.IndentingXMLStreamWriter;
import javax.xml.XMLConstants;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;

/**
 * A response counterpart to {@link RequestBody}. It is inherently immutable and exposes methods to write the content
 * to an {@link OutputStream}.
 */
@NonNullByDefault
public abstract class ReplyBody implements Immutable {
    private static final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newFactory();
    private static final String PRETTY_PRINT_INDENT = "  ";

    private final boolean prettyPrint;

    ReplyBody(final boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Write the content of this body as a JSON document.
     *
     * @param out output stream
     * @throws IOException if an IO error occurs.
     */
    public final void writeJSON(final OutputStream out) throws IOException {
        writeJSON(requireNonNull(out), prettyPrint);
    }

    abstract void writeJSON(OutputStream out, boolean prettyPrint) throws IOException;

    /**
     * Write the content of this body as an XML document.
     *
     * @param out output stream
     * @throws IOException if an IO error occurs.
     */
    public final void writeXML(final OutputStream out) throws IOException {
        writeXML(requireNonNull(out), prettyPrint);
    }

    abstract void writeXML(OutputStream out, boolean prettyPrint) throws IOException;

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("prettyPrint", prettyPrint);
    }

    static final JsonWriter createJsonWriter(final OutputStream out, final boolean prettyPrint) {
        final var ret = JsonWriterFactory.createJsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        ret.setIndent(prettyPrint ? PRETTY_PRINT_INDENT : "");
        return ret;
    }

    static final XMLStreamWriter createXmlWriter(final OutputStream out, final boolean prettyPrint) throws IOException {
        final var xmlWriter = createXmlWriter(out);
        return prettyPrint ? new IndentingXMLStreamWriter(xmlWriter) : xmlWriter;
    }

    private static XMLStreamWriter createXmlWriter(final OutputStream out) throws IOException {
        try {
            return XML_FACTORY.createXMLStreamWriter(out, StandardCharsets.UTF_8.name());
        } catch (XMLStreamException | FactoryConfigurationError e) {
            throw new IOException(e);
        }
    }

    static final void writeElements(final XMLStreamWriter xmlWriter, final RestconfNormalizedNodeWriter nnWriter,
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
