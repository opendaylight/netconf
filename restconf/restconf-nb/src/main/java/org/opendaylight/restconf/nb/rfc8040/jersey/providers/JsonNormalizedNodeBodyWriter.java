/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

@Provider
@Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
public final class JsonNormalizedNodeBodyWriter extends AbstractNormalizedNodeBodyWriter {
    private static final int DEFAULT_INDENT_SPACES_NUM = 2;

    @Override
    void writeOperationOutput(final SchemaInferenceStack stack, final QueryParameters writerParameters,
            final ContainerNode output, final OutputStream entityStream) throws IOException {
        // RpcDefinition/ActionDefinition is not supported as initial codec in JSONStreamWriter, so we need to emit
        // initial output declaration
        try (var jsonWriter = createJsonWriter(entityStream, writerParameters.prettyPrint())) {
            final var module = stack.currentModule();
            jsonWriter.beginObject().name(module.argument().getLocalName() + ":output").beginObject();

            final var nnWriter = createNormalizedNodeWriter(stack.toInference(), jsonWriter, writerParameters,
                module.namespace().argument());
            writeChildren(nnWriter, output);
            nnWriter.flush();

            jsonWriter.endObject().endObject();
        }
    }

    @Override
    void writeData(final SchemaInferenceStack stack, final QueryParameters writerParameters, final NormalizedNode data,
            final OutputStream entityStream) throws IOException {
        if (!stack.isEmpty()) {
            stack.exit();
        }

        // RESTCONF allows returning one list item. We need to wrap it in map node in order to serialize it properly
        final var toSerialize = data instanceof MapEntryNode mapEntry
            ? ImmutableNodes.mapNodeBuilder(data.name().getNodeType()).withChild(mapEntry).build() : data;

        try (var jsonWriter = createJsonWriter(entityStream, writerParameters.prettyPrint())) {
            jsonWriter.beginObject();

            final var nnWriter = createNormalizedNodeWriter(stack.toInference(), jsonWriter, writerParameters, null);
            nnWriter.write(toSerialize);
            nnWriter.flush();

            jsonWriter.endObject().flush();
        }
    }

    private static void writeChildren(final RestconfNormalizedNodeWriter nnWriter, final ContainerNode data)
            throws IOException {
        for (var child : data.body()) {
            nnWriter.write(child);
        }
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(final Inference inference,
            final JsonWriter jsonWriter, final QueryParameters writerParameters,
            final @Nullable XMLNamespace initialNamespace) {
        // TODO: Performance: Cache JSON Codec factory and schema context
        final var codecs = JSONCodecFactorySupplier.RFC7951.getShared(inference.modelContext());
        return ParameterAwareNormalizedNodeWriter.forStreamWriter(
            JSONNormalizedNodeStreamWriter.createNestedWriter(codecs, inference,
                initialNamespace, jsonWriter), writerParameters.depth(), writerParameters.fields());
    }

    private static JsonWriter createJsonWriter(final OutputStream entityStream,
            final @Nullable PrettyPrintParam prettyPrint) {
        final var outputWriter = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8);
        return prettyPrint != null && prettyPrint.value()
            ? JsonWriterFactory.createJsonWriter(outputWriter, DEFAULT_INDENT_SPACES_NUM)
                : JsonWriterFactory.createJsonWriter(outputWriter);
    }
}
