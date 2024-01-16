/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.dtcl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter.createNestedWriter;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Collection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.DataChangedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.tree.api.ModificationType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

final class JSONDataTreeCandidateSerializer extends DataTreeCandidateSerializer<IOException> {
    private static final XMLNamespace SAL_REMOTE_NS = DataChangedNotification.QNAME.getNamespace();
    private static final Absolute DATA_CHANGE_EVENT = Absolute.of(DataChangedNotification.QNAME, DataChangeEvent.QNAME);

    private final JsonWriter jsonWriter;

    JSONDataTreeCandidateSerializer(final EffectiveModelContext context, final JsonWriter jsonWriter) {
        super(context);
        this.jsonWriter = requireNonNull(jsonWriter);
    }

    @Override
    void serializeData(final Inference parent, final Collection<PathArgument> dataPath,
            final DataTreeCandidateNode candidate, final boolean skipData) throws IOException {
        jsonWriter.beginObject();

        final var modificationType = candidate.modificationType();
        if (modificationType != ModificationType.UNMODIFIED) {
            final var codecs = JSONCodecFactorySupplier.RFC7951.getShared(parent.modelContext());
            try (var writer = createNestedWriter(codecs, DATA_CHANGE_EVENT, SAL_REMOTE_NS, jsonWriter)) {
                writer.startLeafNode(PATH_NID);
                writer.scalarValue(YangInstanceIdentifier.of(dataPath));
                writer.endNode();

                writer.startLeafNode(OPERATION_NID);
                writer.scalarValue(modificationTypeToOperation(candidate));
                writer.endNode();
            }

            if (!skipData) {
                final var dataAfter = getDataAfter(candidate);
                if (dataAfter != null) {
                    jsonWriter.name("data").beginObject();
                    try (var writer = createNestedWriter(codecs, parent, SAL_REMOTE_NS, jsonWriter)) {
                        NormalizedNodeWriter.forStreamWriter(writer).write(dataAfter).flush();
                    }
                    jsonWriter.endObject();
                }
            }
        }

        jsonWriter.endObject();
    }
}
