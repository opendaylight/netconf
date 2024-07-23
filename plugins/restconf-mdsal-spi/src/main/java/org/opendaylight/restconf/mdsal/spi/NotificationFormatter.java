/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import com.google.common.annotations.Beta;
import java.io.IOException;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.server.spi.EventFormatter;
import org.opendaylight.restconf.server.spi.TextParameters;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;

/**
 * Base class for {@link DOMNotification} {@link EventFormatter}s.
 */
@Beta
public abstract class NotificationFormatter extends EventFormatter<DOMNotification> {
    protected NotificationFormatter(final TextParameters textParams) {
        super(textParams);
    }

    protected NotificationFormatter(final TextParameters textParams, final String xpathFilter)
            throws XPathExpressionException {
        super(textParams, xpathFilter);
    }

    @Override
    protected final void fillDocument(final Document doc, final EffectiveModelContext schemaContext,
            final DOMNotification input) throws IOException {
        final var notificationElement = createNotificationElement(doc,
            input instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
        try {
            final var writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(new DOMResult(notificationElement));
            try {
                writeBody(XMLStreamNormalizedNodeStreamWriter.create(writer, schemaContext, input.getType()),
                    input.getBody());
            } finally {
                writer.close();
            }
        } catch (final XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }
        doc.appendChild(notificationElement);
    }
}
