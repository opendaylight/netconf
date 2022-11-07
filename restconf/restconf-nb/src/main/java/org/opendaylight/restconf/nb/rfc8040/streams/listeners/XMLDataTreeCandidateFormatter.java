/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationFormatter.DATA_CHANGED_NOTIFICATION_ELEMENT;
import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationFormatter.SAL_REMOTE_NAMESPACE;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collection;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public final class XMLDataTreeCandidateFormatter extends DataTreeCandidateFormatter {
    private static final XMLDataTreeCandidateFormatter INSTANCE = new XMLDataTreeCandidateFormatter();

    static final DataTreeCandidateFormatterFactory FACTORY =
        new DataTreeCandidateFormatterFactory() {
            @Override
            public XMLDataTreeCandidateFormatter getFormatter(final String xpathFilter)
                    throws XPathExpressionException {
                return new XMLDataTreeCandidateFormatter(xpathFilter);
            }

            @Override
            public XMLDataTreeCandidateFormatter getFormatter() {
                return INSTANCE;
            }
        };

    private XMLDataTreeCandidateFormatter() {

    }

    private XMLDataTreeCandidateFormatter(final String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    String createText(final EffectiveModelContext schemaContext, final Collection<DataTreeCandidate> input,
                      final Instant now, final boolean leafNodesOnly, final boolean skipData,
                      final boolean changedLeafNodesOnly) throws Exception {
        final var writer = new StringWriter();
        boolean nonEmpty = false;
        try {
            final var xmlStreamWriter = NotificationFormatter.createStreamWriterWithNotification(writer, now);

            xmlStreamWriter.setDefaultNamespace(SAL_REMOTE_NAMESPACE);
            xmlStreamWriter.writeStartElement(SAL_REMOTE_NAMESPACE, DATA_CHANGED_NOTIFICATION_ELEMENT);

            final var serializer = new XmlDataTreeCandidateSerializer(schemaContext, xmlStreamWriter);
            for (var candidate : input) {
                nonEmpty |= serializer.serialize(candidate, leafNodesOnly, skipData, changedLeafNodesOnly);
            }

            // data-changed-notification
            xmlStreamWriter.writeEndElement();

            // notification
            xmlStreamWriter.writeEndElement();
            xmlStreamWriter.close();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }

        return nonEmpty ? writer.toString() : null;
    }
}
