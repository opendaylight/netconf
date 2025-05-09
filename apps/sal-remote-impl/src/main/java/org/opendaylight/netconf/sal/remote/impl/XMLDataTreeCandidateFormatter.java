/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.remote.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.restconf.server.spi.TextParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.DataChangedNotification;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class XMLDataTreeCandidateFormatter extends DataTreeCandidateFormatter {
    private static final XMLDataTreeCandidateFormatter EMPTY = new XMLDataTreeCandidateFormatter(TextParameters.EMPTY);

    static final DataTreeCandidateFormatterFactory FACTORY = new DataTreeCandidateFormatterFactory(EMPTY) {
        @Override
        public XMLDataTreeCandidateFormatter newFormatter(final TextParameters textParams) {
            return new XMLDataTreeCandidateFormatter(textParams);
        }
    };

    static final String DATA_CHANGED_NOTIFICATION_ELEMENT = DataChangedNotification.QNAME.getLocalName();
    static final String DATA_CHANGED_NOTIFICATION_NS = DataChangedNotification.QNAME.getNamespace().toString();

    private XMLDataTreeCandidateFormatter(final TextParameters textParams) {
        super(textParams);
    }

    @Override
    protected String createText(final TextParameters params, final EffectiveModelContext schemaContext,
            final List<DataTreeCandidate> input, final Instant now) throws Exception {
        final var writer = new StringWriter();
        boolean nonEmpty = false;
        try {
            final var xmlStreamWriter = createStreamWriterWithNotification(writer, now);
            xmlStreamWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, DATA_CHANGED_NOTIFICATION_ELEMENT,
                DATA_CHANGED_NOTIFICATION_NS);
            xmlStreamWriter.writeDefaultNamespace(DATA_CHANGED_NOTIFICATION_NS);

            final var serializer = new XMLDataTreeCandidateSerializer(schemaContext, xmlStreamWriter);
            for (var candidate : input) {
                nonEmpty |= serializer.serialize(candidate, params);
            }

            // data-changed-notification
            xmlStreamWriter.writeEndElement();

            // notification
            xmlStreamWriter.writeEndElement();
            xmlStreamWriter.close();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write notification content", e);
        }

        return nonEmpty ? writer.toString() : null;
    }
}
