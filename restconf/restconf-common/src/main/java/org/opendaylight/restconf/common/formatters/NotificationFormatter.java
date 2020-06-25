/*
 * Copyright (c) 2020 Pantheon.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import java.io.IOException;
import java.time.Instant;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class NotificationFormatter extends EventFormatter<DOMNotification> {
    protected static final XMLOutputFactory XML_OUTPUT_FACTORY;

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    NotificationFormatter() {

    }

    public NotificationFormatter(final String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    void fillDocument(Document doc, EffectiveModelContext schemaContext, DOMNotification input) throws IOException {
        final Element notificationElement = doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0",
                "notification");
        final Element eventTimeElement = doc.createElement("eventTime");
        // FIXME: we should have eventTime available from DOM
        eventTimeElement.setTextContent(toRFC3339(Instant.now()));
        notificationElement.appendChild(eventTimeElement);

        final Element notificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "create-notification-stream");
        final Element dataElement = doc.createElement("notification");
        final DOMResult result = new DOMResult(dataElement);
        try {
            final XMLStreamWriter writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(result);
            try {
                writeNotificationBody(XMLStreamNormalizedNodeStreamWriter.create(writer, schemaContext,
                        input.getType()), input.getBody());
            } finally {
                writer.close();
            }
        } catch (final XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }
        notificationElement.appendChild(notificationEventElement);
        doc.appendChild(notificationElement);
    }

    static void writeNotificationBody(final NormalizedNodeStreamWriter writer, final ContainerNode body)
            throws IOException {
        try (NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(writer)) {
            nodeWriter.write(body);
        }
    }
}
