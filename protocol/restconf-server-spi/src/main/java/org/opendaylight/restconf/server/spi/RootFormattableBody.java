/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;

/**
 * A {@link NormalizedFormattableBody} for a data root.
 */
@NonNullByDefault
final class RootFormattableBody extends NormalizedFormattableBody<ContainerNode> {
    RootFormattableBody(final DatabindContext databind, final NormalizedNodeWriterFactory writerFactory,
            final ContainerNode data) {
        super(databind, writerFactory, data);
    }

    @Override
    protected void formatToJSON(final JSONCodecFactory codecs, final ContainerNode data, final JsonWriter writer)
            throws IOException {
        writer.beginObject();

        final var nnWriter = newWriter(JSONNormalizedNodeStreamWriter.createNestedWriter(codecs, writer, null));
        // FIXME: we should be remapping namespace here
        nnWriter.write(data);
        nnWriter.flush();

        writer.endObject();
    }

    @Override
    protected void formatToXML(final XmlCodecFactory codecs, final ContainerNode data, final XMLStreamWriter writer)
            throws IOException, XMLStreamException {
        // FIXME: we should be remapping namespace here
        final QName nodeType = data.name().getNodeType();
        final String namespace = nodeType.getNamespace().toString();

        writer.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, nodeType.getLocalName(), namespace);
        writer.writeDefaultNamespace(namespace);

        if (!data.isEmpty()) {
            final var nnWriter = newWriter(
                XMLStreamNormalizedNodeStreamWriter.create(writer, codecs.modelContext()));
            nnWriter.write(data);
            nnWriter.flush();
        }

        writer.writeEndElement();
    }
}
