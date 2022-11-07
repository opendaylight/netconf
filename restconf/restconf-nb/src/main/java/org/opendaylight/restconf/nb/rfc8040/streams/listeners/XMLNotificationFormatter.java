/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class XMLNotificationFormatter extends NotificationFormatter {
    private static final XMLNotificationFormatter INSTANCE = new XMLNotificationFormatter();

    static final NotificationFormatterFactory FACTORY = new NotificationFormatterFactory() {
        @Override
        public XMLNotificationFormatter getFormatter(final String xpathFilter) throws XPathExpressionException {
            return new XMLNotificationFormatter(xpathFilter);
        }

        @Override
        public XMLNotificationFormatter getFormatter() {
            return INSTANCE;
        }
    };

    XMLNotificationFormatter() {

    }

    XMLNotificationFormatter(final String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    String createText(final EffectiveModelContext schemaContext, final DOMNotification input, final Instant now,
                      final boolean leafNodesOnly, final boolean skipData, final boolean changedLeafNodesOnly)
            throws IOException {
        final var writer = new StringWriter();

        try {
            final var xmlStreamWriter = NotificationFormatter.createStreamWriterWithNotification(writer, now);
            final var nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlStreamWriter, schemaContext,
                input.getType());

            try (var nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter)) {
                nnWriter.write(input.getBody());
                nnWriter.flush();

                xmlStreamWriter.writeEndElement();
                xmlStreamWriter.writeEndDocument();
                xmlStreamWriter.flush();
            }
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }

        return writer.toString();
    }
}