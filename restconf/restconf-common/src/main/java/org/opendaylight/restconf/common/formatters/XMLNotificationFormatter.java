/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class XMLNotificationFormatter extends NotificationFormatter {
    private static final XMLNotificationFormatter INSTANCE = new XMLNotificationFormatter();

    static final String NOTIFICATION_NAMESPACE = "urn:ietf:params:xml:ns:netconf:notification:1.0";
    static final String NOTIFICATION_ELEMENT = "notification";

    public static final NotificationFormatterFactory FACTORY = new NotificationFormatterFactory() {
        @Override
        public XMLNotificationFormatter getFormatter(final String xpathFilter) throws XPathExpressionException {
            return new XMLNotificationFormatter(xpathFilter);
        }

        @Override
        public XMLNotificationFormatter getFormatter() {
            return INSTANCE;
        }
    };

    public XMLNotificationFormatter() {
    }

    public XMLNotificationFormatter(String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    String createText(EffectiveModelContext schemaContext, DOMNotification input, Instant now,
                      boolean leafNodesOnly, boolean skipData)
            throws IOException {
        final StringWriter writer = new StringWriter();
        try {
            final XMLStreamWriter xmlStreamWriter = XML_OUTPUT_FACTORY.createXMLStreamWriter(writer);
            xmlStreamWriter.setDefaultNamespace(NOTIFICATION_NAMESPACE);

            xmlStreamWriter.writeStartElement(NOTIFICATION_NAMESPACE, NOTIFICATION_ELEMENT);
            xmlStreamWriter.writeDefaultNamespace(NOTIFICATION_NAMESPACE);

            xmlStreamWriter.writeStartElement("eventTime");
            xmlStreamWriter.writeCharacters(toRFC3339(now));
            xmlStreamWriter.writeEndElement();

            final NormalizedNodeStreamWriter nnStreamWriter =
                    XMLStreamNormalizedNodeStreamWriter.create(xmlStreamWriter, schemaContext, input.getType());

            final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter);
            nnWriter.write(input.getBody());
            nnWriter.flush();

            xmlStreamWriter.writeEndElement();
            xmlStreamWriter.writeEndDocument();
            xmlStreamWriter.flush();

            nnWriter.close();

            return writer.toString();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }
    }
}
