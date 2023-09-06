/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationFormatter.DATA_CHANGE_EVENT_ELEMENT;

import java.util.Collection;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.tree.api.ModificationType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

final class XmlDataTreeCandidateSerializer extends AbstractWebsocketSerializer<Exception> {
    private final XMLStreamWriter xmlWriter;

    XmlDataTreeCandidateSerializer(final EffectiveModelContext context, final XMLStreamWriter xmlWriter) {
        super(context);
        this.xmlWriter = requireNonNull(xmlWriter);
    }

    @Override
    void serializeData(final Inference parent, final Collection<PathArgument> dataPath,
            final DataTreeCandidateNode candidate, final boolean skipData) throws Exception {
        final var modificationType = candidate.modificationType();
        if (modificationType != ModificationType.UNMODIFIED) {
            xmlWriter.writeStartElement(DATA_CHANGE_EVENT_ELEMENT);
            xmlWriter.writeStartElement("path");
            // FIXME: use proper XML codec for YangInstanceIdentifier
            xmlWriter.writeCharacters(convertPath(dataPath));
            xmlWriter.writeEndElement();

            xmlWriter.writeStartElement("operation");
            xmlWriter.writeCharacters(modificationTypeToOperation(candidate));
            xmlWriter.writeEndElement();

            if (!skipData) {
                final var dataAfter = getDataAfter(candidate);
                if (dataAfter != null) {
                    xmlWriter.writeStartElement("data");
                    final var nnWriter = NormalizedNodeWriter.forStreamWriter(
                        XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, parent));
                    nnWriter.write(dataAfter);
                    nnWriter.flush();
                    xmlWriter.writeEndElement();
                }
            }

            xmlWriter.writeEndElement();
        }
    }
}
