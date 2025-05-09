/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.server.spi.TextParameters;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class XMLNotificationFormatter extends NotificationFormatter {
    @VisibleForTesting
    static final XMLNotificationFormatter EMPTY = new XMLNotificationFormatter(TextParameters.EMPTY);
    static final NotificationFormatterFactory FACTORY = new NotificationFormatterFactory(EMPTY) {
        @Override
        public XMLNotificationFormatter newFormatter(final TextParameters textParams) {
            return new XMLNotificationFormatter(textParams);
        }
    };

    XMLNotificationFormatter(final TextParameters textParams) {
        super(textParams);
    }

    @Override
    protected String createText(final TextParameters params, final EffectiveModelContext schemaContext,
            final DOMNotification input, final Instant now) throws IOException {
        final var writer = new StringWriter();

        try {
            final var xmlStreamWriter = createStreamWriterWithNotification(writer, now);
            try (var nnWriter = NormalizedNodeWriter.forStreamWriter(XMLStreamNormalizedNodeStreamWriter.create(
                    xmlStreamWriter, schemaContext, input.getType()))) {
                nnWriter.write(input.getBody());
                nnWriter.flush();

                xmlStreamWriter.writeEndElement();
                xmlStreamWriter.writeEndDocument();
                xmlStreamWriter.flush();
            }
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }

        return writer.toString();
    }
}