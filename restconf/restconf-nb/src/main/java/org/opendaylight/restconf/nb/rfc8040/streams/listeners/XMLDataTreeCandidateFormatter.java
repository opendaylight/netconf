/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationFormatter.XML_OUTPUT_FACTORY;
import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.XMLNotificationFormatter.DATA_CHANGED_NAMESPACE;
import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.XMLNotificationFormatter.DATA_CHANGED_NOTIFICATION_ELEMENT;
import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.XMLNotificationFormatter.NOTIFICATION_ELEMENT;
import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.XMLNotificationFormatter.NOTIFICATION_NAMESPACE;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collection;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
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
        StringWriter writer = new StringWriter();
        boolean emptyDataChangeEvent;

        final XMLStreamWriter xmlStreamWriter;
        try {
            xmlStreamWriter = XML_OUTPUT_FACTORY.createXMLStreamWriter(writer);
            xmlStreamWriter.setDefaultNamespace(NOTIFICATION_NAMESPACE);

            xmlStreamWriter.writeStartElement(NOTIFICATION_NAMESPACE, NOTIFICATION_ELEMENT);
            xmlStreamWriter.writeDefaultNamespace(NOTIFICATION_NAMESPACE);

            xmlStreamWriter.writeStartElement("eventTime");
            xmlStreamWriter.writeCharacters(toRFC3339(now));
            xmlStreamWriter.writeEndElement();

            xmlStreamWriter.setDefaultNamespace(DATA_CHANGED_NAMESPACE);
            xmlStreamWriter.writeStartElement(DATA_CHANGED_NAMESPACE, DATA_CHANGED_NOTIFICATION_ELEMENT);

            final XmlDataTreeCandidateSerializer serializer =
                    new XmlDataTreeCandidateSerializer(schemaContext, xmlStreamWriter);

            for (final DataTreeCandidate candidate : input) {
                serializer.serialize(candidate, leafNodesOnly, skipData, changedLeafNodesOnly);
            }

            // data-changed-notification
            xmlStreamWriter.writeEndElement();

            // notification
            xmlStreamWriter.writeEndElement();
            xmlStreamWriter.close();
            emptyDataChangeEvent = serializer.isEmptyDataChangedEvent();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }

        return emptyDataChangeEvent ? null : writer.toString();
    }
}
