/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import java.io.IOException;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.server.spi.XPathEventFilter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;

/**
 * An XPath-based notification filter.
 */
@NonNullByDefault
public final class NotificationXPathEventFilter extends XPathEventFilter<DOMNotification> {
    public NotificationXPathEventFilter(final String expression) throws XPathExpressionException {
        super(expression);
    }

    @Override
    protected void fillDocument(final Document doc, final EffectiveModelContext modelContext,
            final DOMNotification event) throws IOException {
        final var notificationElement = createNotificationElement(doc,
            event instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
        try {
            final var writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(new DOMResult(notificationElement));
            try {
                writeBody(XMLStreamNormalizedNodeStreamWriter.create(writer, modelContext, event.getType()),
                    event.getBody());
            } finally {
                writer.close();
            }
        } catch (final XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }
        doc.appendChild(notificationElement);
    }
}
