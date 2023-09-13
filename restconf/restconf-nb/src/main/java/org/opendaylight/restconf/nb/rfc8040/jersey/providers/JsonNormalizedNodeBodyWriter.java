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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

@Provider
@Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
public class JsonNormalizedNodeBodyWriter extends AbstractNormalizedNodeBodyWriter {
    private static final int DEFAULT_INDENT_SPACES_NUM = 2;

    @Override
    public void writeTo(final NormalizedNodePayload context,
                        final Class<?> type,
                        final Type genericType,
                        final Annotation[] annotations,
                        final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws IOException, WebApplicationException {
        if (context.getData() == null) {
            return;
        }

        if (httpHeaders != null) {
            for (var entry : context.getNewHeaders().entrySet()) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        final var pretty = context.getWriterParameters().prettyPrint();
        try (var jsonWriter = createJsonWriter(entityStream, pretty == null ? false : pretty.value())) {
            jsonWriter.beginObject();
            writeNormalizedNode(jsonWriter, context.getInstanceIdentifierContext(), context.getData(),
                context.getWriterParameters().depth(), context.getWriterParameters().fields());
            jsonWriter.endObject();
            jsonWriter.flush();
        }
    }

    private static void writeNormalizedNode(final JsonWriter jsonWriter, final InstanceIdentifierContext context,
            final NormalizedNode data, final DepthParam depth, final List<Set<QName>> fields) throws IOException {
        final var schemaNode = context.getSchemaNode();
        if (schemaNode instanceof RpcDefinition rpc) {
            // RpcDefinition is not supported as initial codec in JSONStreamWriter, so we need to emit initial output
            // declaration
            final var stack = SchemaInferenceStack.of(context.getSchemaContext());
            stack.enterSchemaTree(rpc.getQName());
            stack.enterSchemaTree(rpc.getOutput().getQName());

            jsonWriter.name(stack.currentModule().argument().getLocalName() + ":output");
            jsonWriter.beginObject();

            final var nnWriter = createNormalizedNodeWriter(context, stack.toInference(), jsonWriter, depth, fields,
                rpc.getQName().getNamespace());
            writeChildren(nnWriter, (ContainerNode) data);
            nnWriter.flush();

            jsonWriter.endObject();
        } else if (schemaNode instanceof ActionDefinition action) {
            // FIXME: why is this different from RPC?!

            // ActionDefinition is not supported as initial codec in JSONStreamWriter, so we need to emit initial output
            // declaration
            final var stack = context.inference().toSchemaInferenceStack();
            stack.enterSchemaTree(action.getOutput().getQName());

            jsonWriter.name(stack.currentModule().argument().getLocalName() + ":output");
            jsonWriter.beginObject();

            final var nnWriter = createNormalizedNodeWriter(context, stack.toInference(), jsonWriter, depth, fields,
                null);
            writeChildren(nnWriter, (ContainerNode) data);
            nnWriter.flush();

            jsonWriter.endObject();
        } else {
            final var stack = context.inference().toSchemaInferenceStack();
            if (!stack.isEmpty()) {
                stack.exit();
            }

            // RESTCONF allows returning one list item. We need to wrap it in map node in order to serialize it properly
            final var toSerialize = data instanceof MapEntryNode mapEntry
                ? ImmutableNodes.mapNodeBuilder(data.name().getNodeType()).withChild(mapEntry).build() : data;

            final var nnWriter = createNormalizedNodeWriter(context, stack.toInference(), jsonWriter, depth, fields,
                null);
            nnWriter.write(toSerialize);
            nnWriter.flush();
        }
    }

    private static void writeChildren(final RestconfNormalizedNodeWriter nnWriter, final ContainerNode data)
            throws IOException {
        for (var child : data.body()) {
            nnWriter.write(child);
        }
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(final InstanceIdentifierContext context,
            final Inference inference, final JsonWriter jsonWriter, final DepthParam depth,
            final List<Set<QName>> fields, final @Nullable XMLNamespace initialNamespace) {
        // TODO: Performance: Cache JSON Codec factory and schema context
        final var codecs = JSONCodecFactorySupplier.RFC7951.getShared(context.getSchemaContext());
        return ParameterAwareNormalizedNodeWriter.forStreamWriter(
            JSONNormalizedNodeStreamWriter.createNestedWriter(codecs, inference,
                initialNamespace, jsonWriter), depth, fields);
    }

    private static JsonWriter createJsonWriter(final OutputStream entityStream, final boolean prettyPrint) {
        final var outputWriter = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8);
        return prettyPrint ? JsonWriterFactory.createJsonWriter(outputWriter, DEFAULT_INDENT_SPACES_NUM)
            : JsonWriterFactory.createJsonWriter(outputWriter);
    }
}
