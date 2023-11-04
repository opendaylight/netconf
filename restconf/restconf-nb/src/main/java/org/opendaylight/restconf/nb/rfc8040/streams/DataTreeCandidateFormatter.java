/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import java.io.IOException;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.DataChangedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Base formatter for DataTreeCandidates which only handles exporting to a document for filter checking purpose.
 */
abstract class DataTreeCandidateFormatter extends EventFormatter<List<DataTreeCandidate>> {
    private static final String DATA_CHANGE_EVENT_ELEMENT = DataChangeEvent.QNAME.getLocalName();
    static final String DATA_CHANGED_NOTIFICATION_ELEMENT = DataChangedNotification.QNAME.getLocalName();
    static final String DATA_CHANGED_NOTIFICATION_NS = DataChangedNotification.QNAME.getNamespace().toString();

    DataTreeCandidateFormatter(final TextParameters textParams) {
        super(textParams);
    }

    DataTreeCandidateFormatter(final TextParameters textParams, final String xpathFilter)
            throws XPathExpressionException {
        super(textParams, xpathFilter);
    }

    @Override
    final void fillDocument(final Document doc, final EffectiveModelContext schemaContext,
            final List<DataTreeCandidate> input) throws IOException {
        final Element notificationElement = NotificationFormatter.createNotificationElement(doc);
        final Element notificationEventElement = doc.createElementNS(
            DATA_CHANGED_NOTIFICATION_NS, DATA_CHANGED_NOTIFICATION_ELEMENT);

        for (DataTreeCandidate candidate : input) {
            final Element dataChangedElement = doc.createElement(DATA_CHANGE_EVENT_ELEMENT);
            try {
                final Element dataElement = doc.createElement("data");
                final DOMResult domResult = new DOMResult(dataElement);
                final XMLStreamWriter writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(domResult);

                writeCandidate(XMLStreamNormalizedNodeStreamWriter.create(writer, schemaContext,
                    candidate.getRootPath()), candidate);

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
        final var dataAfter = candidate.getRootNode().dataAfter();
        if (dataAfter != null) {
            try (var nodeWriter = NormalizedNodeWriter.forStreamWriter(writer)) {
                nodeWriter.write(dataAfter);
            }
        }
    }
}
