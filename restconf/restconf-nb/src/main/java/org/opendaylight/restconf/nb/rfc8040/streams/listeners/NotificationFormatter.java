/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.DataChangedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

abstract class NotificationFormatter extends EventFormatter<DOMNotification> {
    private static final String NOTIFICATION_NAMESPACE = "urn:ietf:params:xml:ns:netconf:notification:1.0";
    private static final String NOTIFICATION_ELEMENT = "notification";

    static final String SAL_REMOTE_NAMESPACE = DataChangedNotification.QNAME.getNamespace().toString();
    static final String DATA_CHANGED_NOTIFICATION_ELEMENT = DataChangedNotification.QNAME.getLocalName();
    static final String DATA_CHANGE_EVENT_ELEMENT = DataChangeEvent.QNAME.getLocalName();

    static final XMLOutputFactory XML_OUTPUT_FACTORY;

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    NotificationFormatter() {

    }

    NotificationFormatter(final String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    final void fillDocument(final Document doc, final EffectiveModelContext schemaContext, final DOMNotification input)
            throws IOException {
        final var notificationElement = createNotificationElement(doc,
            input instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
        final var notificationEventElement = doc.createElementNS(SAL_REMOTE_NAMESPACE, "create-notification-stream");
        final var dataElement = doc.createElement("notification");
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

    /**
     * Generating base element of every notification.
     *
     * @param doc base {@link Document}
     * @return element of {@link Document}
     */
    static @NonNull Element createNotificationElement(final Document doc) {
        return createNotificationElement(doc, Instant.now());
    }

    static @NonNull Element createNotificationElement(final Document doc, final Instant now) {
        final var notificationElement = doc.createElementNS(NOTIFICATION_NAMESPACE, NOTIFICATION_ELEMENT);
        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(toRFC3339(now));
        notificationElement.appendChild(eventTimeElement);
        return notificationElement;
    }

    static @NonNull XMLStreamWriter createStreamWriterWithNotification(final Writer writer, final Instant now)
            throws XMLStreamException {
        final var xmlStreamWriter = XML_OUTPUT_FACTORY.createXMLStreamWriter(writer);
        xmlStreamWriter.setDefaultNamespace(NOTIFICATION_NAMESPACE);

        xmlStreamWriter.writeStartElement(NOTIFICATION_NAMESPACE, NOTIFICATION_ELEMENT);
        xmlStreamWriter.writeDefaultNamespace(NOTIFICATION_NAMESPACE);

        xmlStreamWriter.writeStartElement("eventTime");
        xmlStreamWriter.writeCharacters(toRFC3339(now));
        xmlStreamWriter.writeEndElement();
        return xmlStreamWriter;
    }

    static @NonNull XMLStreamWriter createStreamWriterWithNotification(final Writer writer, final Instant now,
            final String deviceId)
            throws XMLStreamException {
        XMLStreamWriter xmlStreamWriter = createStreamWriterWithNotification(writer, now);
        if (deviceId != null) {
            xmlStreamWriter.writeStartElement("deviceId");
            xmlStreamWriter.writeCharacters(deviceId);
            xmlStreamWriter.writeEndElement();
        }
        return xmlStreamWriter;
    }
}
