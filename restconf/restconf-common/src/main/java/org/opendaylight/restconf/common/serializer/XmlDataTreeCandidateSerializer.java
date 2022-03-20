/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.serializer;

import static org.opendaylight.restconf.common.formatters.XMLNotificationFormatter.DATA_CHANGE_EVENT_ELEMENT;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class XmlDataTreeCandidateSerializer extends AbstractWebsocketSerializer<Exception> {

    private final EffectiveModelContext context;
    private final XMLStreamWriter xmlWriter;

    public XmlDataTreeCandidateSerializer(final EffectiveModelContext context, final XMLStreamWriter xmlWriter) {
        this.context = context;
        this.xmlWriter = xmlWriter;
    }

    @Override
    void serializeData(final Collection<PathArgument> nodePath, final DataTreeCandidateNode candidate,
            final boolean skipData) throws IOException, XMLStreamException {
        final SchemaPath path = SchemaPath.create(nodePath.stream()
                .filter(p -> !(p instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates))
                .map(PathArgument::getNodeType).collect(Collectors.toList()), true);
        final NormalizedNodeStreamWriter nodeStreamWriter =
                XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, context, path.getParent());

        xmlWriter.writeStartElement(DATA_CHANGE_EVENT_ELEMENT);
        serializePath(nodePath);

        if (!skipData && candidate.getDataAfter().isPresent()) {
            xmlWriter.writeStartElement("data");
            NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(nodeStreamWriter);
            nnWriter.write(candidate.getDataAfter().get());
            nnWriter.flush();

            xmlWriter.writeEndElement();
        }
        serializeOperation(candidate);

        xmlWriter.writeEndElement();
    }

    @Override
    public void serializePath(final Collection<PathArgument> pathArguments) throws XMLStreamException {
        xmlWriter.writeStartElement("path");
        xmlWriter.writeCharacters(convertPath(pathArguments));
        xmlWriter.writeEndElement();
    }

    @Override
    public void serializeOperation(final DataTreeCandidateNode candidate) throws XMLStreamException {
        xmlWriter.writeStartElement("operation");
        xmlWriter.writeCharacters(modificationTypeToOperation(candidate, candidate.getModificationType()));
        xmlWriter.writeEndElement();
    }
}
