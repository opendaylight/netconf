/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class EventFormatter<T> implements Immutable {
    private static final XPathFactory XPF = XPathFactory.newInstance();

    // FIXME: NETCONF-369: XPath operates without namespace context, therefore we need an namespace-unaware builder.
    //        Once it is fixed we can use UntrustedXML instead.
    private static final @NonNull DocumentBuilderFactory DBF;

    static {
        final DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setCoalescing(true);
        f.setExpandEntityReferences(false);
        f.setIgnoringElementContentWhitespace(true);
        f.setIgnoringComments(true);
        f.setXIncludeAware(false);
        try {
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        DBF = f;
    }

    static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    private final TextParameters textParams;
    private final XPathExpression filter;

    EventFormatter(final TextParameters textParams)  {
        this.textParams = requireNonNull(textParams);
        filter = null;
    }

    EventFormatter(final TextParameters params, final String xpathFilter) throws XPathExpressionException {
        textParams = requireNonNull(params);

        final XPath xpath;
        synchronized (XPF) {
            xpath = XPF.newXPath();
        }
        // FIXME: NETCONF-369: we need to bind the namespace context here and for that we need the SchemaContext
        filter = xpath.compile(xpathFilter);
    }

    final @Nullable String eventData(final EffectiveModelContext schemaContext, final T input, final Instant now)
            throws Exception {
        return filterMatches(schemaContext, input, now) ? createText(textParams, schemaContext, input, now) : null;
    }

    /**
     * Export the provided input into the provided document so we can verify whether a filter matches the content.
     *
     * @param doc the document to fill
     * @param schemaContext context to use for the export
     * @param input data to export
     * @throws IOException if any IOException occurs during export to the document
     */
    abstract void fillDocument(Document doc, EffectiveModelContext schemaContext, T input) throws IOException;

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
    abstract String createText(TextParameters params, EffectiveModelContext schemaContext, T input, Instant now)
        throws Exception;

    private boolean filterMatches(final EffectiveModelContext schemaContext, final T input, final Instant now)
            throws IOException {
        if (filter == null) {
            return true;
        }

        final Document doc;
        try {
            doc = DBF.newDocumentBuilder().newDocument();
        } catch (final ParserConfigurationException e) {
            throw new IOException("Failed to create a new document", e);
        }
        fillDocument(doc, schemaContext, input);

        final Boolean eval;
        try {
            eval = (Boolean) filter.evaluate(doc, XPathConstants.BOOLEAN);
        } catch (final XPathExpressionException e) {
            throw new IllegalStateException("Failed to evaluate expression " + filter, e);
        }

        return eval;
    }

    /**
     * Formats data specified by RFC3339.
     *
     * @param now time stamp
     * @return Data specified by RFC3339.
     */
    static final String toRFC3339(final Instant now) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(now, ZoneId.systemDefault()));
    }

    static final @NonNull Element createNotificationElement(final Document doc, final Instant now) {
        final var notificationElement = doc.createElementNS(NamespaceURN.NOTIFICATION, "notification");
        final var eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(toRFC3339(now));
        notificationElement.appendChild(eventTimeElement);
        return notificationElement;
    }

    static final @NonNull XMLStreamWriter createStreamWriterWithNotification(final Writer writer, final Instant now)
            throws XMLStreamException {
        final var xmlStreamWriter = XML_OUTPUT_FACTORY.createXMLStreamWriter(writer);
        xmlStreamWriter.setDefaultNamespace(NamespaceURN.NOTIFICATION);

        xmlStreamWriter.writeStartElement(NamespaceURN.NOTIFICATION, "notification");
        xmlStreamWriter.writeDefaultNamespace(NamespaceURN.NOTIFICATION);

        xmlStreamWriter.writeStartElement("eventTime");
        xmlStreamWriter.writeCharacters(toRFC3339(now));
        xmlStreamWriter.writeEndElement();
        return xmlStreamWriter;
    }

    static final void writeBody(final NormalizedNodeStreamWriter writer, final NormalizedNode body) throws IOException {
        try (var nodeWriter = NormalizedNodeWriter.forStreamWriter(writer)) {
            nodeWriter.write(body);
        }
    }
}
