/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationFormatter.XML_OUTPUT_FACTORY;
import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.XMLNotificationFormatter.DATA_CHANGE_EVENT_ELEMENT;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Base formatter for DataTreeCandidates which only handles exporting to a document for filter checking purpose.
 */
abstract class DataTreeCandidateFormatter extends EventFormatter<Collection<DataTreeCandidate>> {
    DataTreeCandidateFormatter() {

    }

    DataTreeCandidateFormatter(final String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    final void fillDocument(final Document doc, final EffectiveModelContext schemaContext,
            final Collection<DataTreeCandidate> input) throws IOException {
        final Element notificationElement = doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0",
                "notification");
        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(toRFC3339(Instant.now()));
        notificationElement.appendChild(eventTimeElement);

        final Element notificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "data-changed-notification");

        for (DataTreeCandidate candidate : input) {
            final Element dataChangedElement = doc.createElement(DATA_CHANGE_EVENT_ELEMENT);
            try {
                final Element dataElement = doc.createElement("data");
                final DOMResult domResult = new DOMResult(dataElement);
                final XMLStreamWriter writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(domResult);

                final SchemaPath path = SchemaPath.create(candidate.getRootPath().getPathArguments().stream()
                        .filter(p -> !(p instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates))
                        .map(YangInstanceIdentifier.PathArgument::getNodeType).collect(Collectors.toList()), true);

                writeCandidate(XMLStreamNormalizedNodeStreamWriter.create(writer, schemaContext,
                        path), candidate);

                dataChangedElement.appendChild(dataElement);
            } catch (final XMLStreamException e) {
                throw new IOException("Failed to write notification content", e);
            }
            notificationElement.appendChild(notificationEventElement);
        }
        doc.appendChild(notificationElement);
    }

    static void writeCandidate(final NormalizedNodeStreamWriter writer, final DataTreeCandidate candidate)
            throws IOException {
        if (candidate.getRootNode().getDataAfter().isPresent()) {
            try (NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(writer)) {
                nodeWriter.write(candidate.getRootNode().getDataAfter().get());
            }
        }
    }
}
