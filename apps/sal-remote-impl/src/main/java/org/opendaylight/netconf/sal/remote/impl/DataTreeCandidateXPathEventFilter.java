/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.remote.impl;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.XPathEventFilter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;

@NonNullByDefault
final class DataTreeCandidateXPathEventFilter extends XPathEventFilter<List<DataTreeCandidate>> {
    private static final String DATA_CHANGE_EVENT_ELEMENT = DataChangeEvent.QNAME.getLocalName();

    DataTreeCandidateXPathEventFilter(final String expression) throws XPathExpressionException {
        super(expression);
    }

    @Override
    protected void fillDocument(final Document doc, final EffectiveModelContext modelContext,
            final List<DataTreeCandidate> event) throws IOException {
        final var notificationElement = createNotificationElement(doc, Instant.now());
        final var notificationEventElement = doc.createElementNS(
            XMLDataTreeCandidateFormatter.DATA_CHANGED_NOTIFICATION_NS,
            XMLDataTreeCandidateFormatter.DATA_CHANGED_NOTIFICATION_ELEMENT);

        for (var candidate : event) {
            final var dataChangedElement = doc.createElement(DATA_CHANGE_EVENT_ELEMENT);
            try {
                final var dataElement = doc.createElement("data");
                final var dataAfter = candidate.getRootNode().dataAfter();
                if (dataAfter != null) {
                    try (var writer = XMLStreamNormalizedNodeStreamWriter.create(
                            XML_OUTPUT_FACTORY.createXMLStreamWriter(new DOMResult(dataElement)), modelContext,
                            candidate.getRootPath())) {
                        writeBody(writer, dataAfter);
                    }
                }
                dataChangedElement.appendChild(dataElement);
            } catch (XMLStreamException e) {
                throw new IOException("Failed to write notification content", e);
            }
            notificationElement.appendChild(notificationEventElement);
        }
        doc.appendChild(notificationElement);
    }
}
