/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import java.time.Instant;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Abstract base class for XPath filters.
 *
 * @param <T> the type of events
 */
@NonNullByDefault
public abstract non-sealed class XPathEventFilter<T> extends EventFilter<T> {
    private static final XPathFactory XPF = XPathFactory.newInstance();

    // FIXME: NETCONF-369: XPath operates without namespace context, therefore we need an namespace-unaware builder.
    //        Once it is fixed we can use UntrustedXML instead.
    private static final DocumentBuilderFactory DBF;

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

    protected static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    private final String expression;
    private final XPathExpression filter;

    protected XPathEventFilter(final String expression) throws XPathExpressionException {
        this.expression = requireNonNull(expression);

        final XPath xpath;
        synchronized (XPF) {
            xpath = XPF.newXPath();
        }
        // FIXME: NETCONF-369: we need to bind the namespace context here and for that we need the SchemaContext
        filter = xpath.compile(expression);
    }

    @Override
    final boolean matches(final EffectiveModelContext modelContext, final T event) throws IOException {
        final Document doc;
        try {
            doc = DBF.newDocumentBuilder().newDocument();
        } catch (final ParserConfigurationException e) {
            throw new IOException("Failed to create a new document", e);
        }
        fillDocument(doc, modelContext, event);

        try {
            return (Boolean) filter.evaluate(doc, XPathConstants.BOOLEAN);
        } catch (final XPathExpressionException e) {
            throw new IOException("Failed to evaluate expression " + filter, e);
        }
    }

    /**
     * Export the provided input into the provided document so we can verify whether a filter matches the content.
     *
     * @param doc the document to fill
     * @param modelContext context to use for the export
     * @param event data to export
     * @throws IOException if any IOException occurs during export to the document
     */
    protected abstract void fillDocument(Document doc, EffectiveModelContext modelContext, T event) throws IOException;

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("expression", expression);
    }

    protected static final Element createNotificationElement(final Document doc, final Instant now) {
        final var notificationElement = doc.createElementNS(NamespaceURN.NOTIFICATION, "notification");
        final var eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(EventFormatter.toRFC3339(now));
        notificationElement.appendChild(eventTimeElement);
        return notificationElement;
    }

    protected static final void writeBody(final NormalizedNodeStreamWriter writer, final NormalizedNode body)
            throws IOException {
        try (var nodeWriter = NormalizedNodeWriter.forStreamWriter(writer)) {
            nodeWriter.write(body);
        }
    }
}
