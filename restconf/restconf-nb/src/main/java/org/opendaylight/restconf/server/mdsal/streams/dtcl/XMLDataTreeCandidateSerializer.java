/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.dtcl;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.DataChangedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.tree.api.ModificationType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

final class XMLDataTreeCandidateSerializer extends DataTreeCandidateSerializer<Exception> {
    private static final @NonNull NodeIdentifier DATA_CHANGE_EVENT_NID = NodeIdentifier.create(DataChangeEvent.QNAME);

    private final XMLStreamWriter xmlWriter;

    XMLDataTreeCandidateSerializer(final EffectiveModelContext context, final XMLStreamWriter xmlWriter) {
        super(context);
        this.xmlWriter = requireNonNull(xmlWriter);
    }

    @Override
    void serializeData(final Inference parent, final Collection<PathArgument> dataPath,
            final DataTreeCandidateNode candidate, final boolean skipData) throws Exception {
        final var modificationType = candidate.modificationType();
        if (modificationType != ModificationType.UNMODIFIED) {
            final var stack = SchemaInferenceStack.of(parent.modelContext());
            stack.enterSchemaTree(DataChangedNotification.QNAME);

            final var writer = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, stack.toInference());
            writer.startMapNode(DATA_CHANGE_EVENT_NID, 1);

            final var path = YangInstanceIdentifier.of(dataPath);
            writer.startMapEntryNode(NodeIdentifierWithPredicates.of(DataChangeEvent.QNAME, PATH_QNAME, path), 4);
            writer.startLeafNode(PATH_NID);
            writer.scalarValue(path);
            writer.endNode();

            writer.startLeafNode(OPERATION_NID);
            writer.scalarValue(modificationTypeToOperation(candidate));
            writer.endNode();

            if (!skipData) {
                final var dataAfter = getDataAfter(candidate);
                if (dataAfter != null) {
                    writer.flush();
                    xmlWriter.writeStartElement(DATA_NAME);
                    final var nnWriter = NormalizedNodeWriter.forStreamWriter(
                        XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, parent));
                    nnWriter.write(dataAfter);
                    nnWriter.flush();
                    xmlWriter.writeEndElement();
                }
            }

            writer.endNode();
            writer.endNode();
        }
    }
}
