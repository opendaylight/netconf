/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.dtcl;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.restconf.nb.rfc8040.streams.EventFormatter;
import org.opendaylight.restconf.nb.rfc8040.streams.TextParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.DataChangedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;

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
    protected final void fillDocument(final Document doc, final EffectiveModelContext schemaContext,
            final List<DataTreeCandidate> input) throws IOException {
        final var notificationElement = createNotificationElement(doc, Instant.now());
        final var notificationEventElement = doc.createElementNS(DATA_CHANGED_NOTIFICATION_NS,
            DATA_CHANGED_NOTIFICATION_ELEMENT);

        for (var candidate : input) {
            final var dataChangedElement = doc.createElement(DATA_CHANGE_EVENT_ELEMENT);
            try {
                final var dataElement = doc.createElement("data");
                final var dataAfter = candidate.getRootNode().dataAfter();
                if (dataAfter != null) {
                    try (var writer = XMLStreamNormalizedNodeStreamWriter.create(
                        XML_OUTPUT_FACTORY.createXMLStreamWriter(new DOMResult(dataElement)), schemaContext,
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
