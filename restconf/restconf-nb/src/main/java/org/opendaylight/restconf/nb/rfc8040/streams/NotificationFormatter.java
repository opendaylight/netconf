/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import java.io.IOException;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStream;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;

abstract class NotificationFormatter extends EventFormatter<DOMNotification> {
    private static final String CREATE_NOTIFICATION_STREAM_ELEMENT = CreateNotificationStream.QNAME.getLocalName();
    private static final String CREATE_NOTIFICATION_STREAM_NS =
        CreateNotificationStream.QNAME.getNamespace().toString();

    NotificationFormatter(final TextParameters textParams) {
        super(textParams);
    }

    NotificationFormatter(final TextParameters textParams, final String xpathFilter) throws XPathExpressionException {
        super(textParams, xpathFilter);
    }

    @Override
    final void fillDocument(final Document doc, final EffectiveModelContext schemaContext, final DOMNotification input)
            throws IOException {
        final var notificationElement = createNotificationElement(doc,
            input instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
        // FIXME: what is this really?!
        final var notificationEventElement = doc.createElementNS(CREATE_NOTIFICATION_STREAM_NS,
            CREATE_NOTIFICATION_STREAM_ELEMENT);
        final var dataElement = doc.createElement("notification");
        try {
            final var writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(new DOMResult(dataElement));
            try {
                writeBody(XMLStreamNormalizedNodeStreamWriter.create(writer, schemaContext, input.getType()),
                    input.getBody());
            } finally {
                writer.close();
            }
        } catch (final XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }
        notificationElement.appendChild(notificationEventElement);
        doc.appendChild(notificationElement);
    }
}
