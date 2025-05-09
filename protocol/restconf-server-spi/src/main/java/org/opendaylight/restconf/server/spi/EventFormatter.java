/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public abstract class EventFormatter<T> implements Immutable {
    private final TextParameters textParams;

    protected EventFormatter(final TextParameters textParams)  {
        this.textParams = requireNonNull(textParams);
    }

    @VisibleForTesting
    public final @Nullable String eventData(final EffectiveModelContext schemaContext, final T input,
            final Instant now) throws Exception {
        return createText(textParams, schemaContext, input, now);
    }

    /**
     * Format the input data into string representation of the data provided.
     *
     * @param params output text parameters
     * @param schemaContext context to use for the export
     * @param input input data
     * @param now time the event happened
     * @return String representation of the formatted data
     * @throws Exception if the underlying formatters fail to export the data to the requested format
     */
    protected abstract String createText(TextParameters params, EffectiveModelContext schemaContext, T input,
        Instant now) throws Exception;

    /**
     * Formats data specified by RFC3339.
     *
     * @param now time stamp
     * @return Data specified by RFC3339.
     */
    protected static final String toRFC3339(final Instant now) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(now, ZoneId.systemDefault()));
    }

    @NonNullByDefault
    protected static final XMLStreamWriter createStreamWriterWithNotification(final Writer writer, final Instant now)
            throws XMLStreamException {
        final var xmlStreamWriter = XPathEventFilter.XML_OUTPUT_FACTORY.createXMLStreamWriter(writer);
        xmlStreamWriter.setDefaultNamespace(NamespaceURN.NOTIFICATION);

        xmlStreamWriter.writeStartElement(NamespaceURN.NOTIFICATION, "notification");
        xmlStreamWriter.writeDefaultNamespace(NamespaceURN.NOTIFICATION);

        xmlStreamWriter.writeStartElement("eventTime");
        xmlStreamWriter.writeCharacters(toRFC3339(now));
        xmlStreamWriter.writeEndElement();
        return xmlStreamWriter;
    }

    @NonNullByDefault
    protected static final void writeBody(final NormalizedNodeStreamWriter writer, final NormalizedNode body)
            throws IOException {
        XPathEventFilter.writeBody(writer, body);
    }
}
