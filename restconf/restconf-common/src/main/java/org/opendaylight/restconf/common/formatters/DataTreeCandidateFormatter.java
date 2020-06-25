/*
 * Copyright (c) 2020 Pantheon.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import static org.opendaylight.restconf.common.formatters.NotificationFormatter.XML_OUTPUT_FACTORY;

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
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class DataTreeCandidateFormatter extends EventFormatter<Collection<DataTreeCandidate>> {

    public DataTreeCandidateFormatter() {
    }

    public DataTreeCandidateFormatter(String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    void fillDocument(Document doc, EffectiveModelContext schemaContext, Collection<DataTreeCandidate> input)
            throws IOException {
        final Element notificationElement = doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0",
                "notification");
        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(toRFC3339(Instant.now()));
        notificationElement.appendChild(eventTimeElement);

        final Element notificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "data-changed-notification");

        for (DataTreeCandidate candidate : input) {
            final Element dataChangedElement = doc.createElement("data-changed-event");
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
