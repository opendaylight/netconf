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
import java.util.Map;
import java.util.Set;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
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
        final NormalizedNode data = context.getData();
        if (data == null) {
            return;
        }

        final InstanceIdentifierContext identifierCtx = context.getInstanceIdentifierContext();
        final var pretty = context.getWriterParameters().prettyPrint();

        try (JsonWriter jsonWriter = createJsonWriter(entityStream, pretty == null ? false : pretty.value())) {
            jsonWriter.beginObject();
            writeNormalizedNode(jsonWriter, identifierCtx, data,
                    context.getWriterParameters().depth(), context.getWriterParameters().fields());
            jsonWriter.endObject();
            jsonWriter.flush();
        }

        if (httpHeaders != null) {
            for (final Map.Entry<String, Object> entry : context.getNewHeaders().entrySet()) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void writeNormalizedNode(final JsonWriter jsonWriter, final InstanceIdentifierContext context,
            final NormalizedNode data, final DepthParam depth, final List<Set<QName>> fields) throws IOException {
        final SchemaNode schemaNode = context.getSchemaNode();
        final RestconfNormalizedNodeWriter nnWriter;
        if (schemaNode instanceof RpcDefinition rpc) {
            final var stack = SchemaInferenceStack.of(context.getSchemaContext());
            stack.enterSchemaTree(rpc.getQName());
            stack.enterSchemaTree(rpc.getOutput().getQName());

            // RpcDefinition is not supported as initial codec in JSONStreamWriter, so we need to emit initial output
            // declaration
            nnWriter = createNormalizedNodeWriter(
                    context,
                    stack.toInference(),
                    jsonWriter,
                    depth,
                    fields);
            final Module module = context.getSchemaContext().findModule(data.name().getNodeType().getModule())
                .orElseThrow();
            jsonWriter.name(module.getName() + ":output");
            jsonWriter.beginObject();
            writeChildren(nnWriter, (ContainerNode) data);
            jsonWriter.endObject();
        } else if (schemaNode instanceof ActionDefinition action) {
            // ActionDefinition is not supported as initial codec in JSONStreamWriter, so we need to emit initial output
            // declaration
            final var stack = context.inference().toSchemaInferenceStack();
            stack.enterSchemaTree(action.getOutput().getQName());

            nnWriter = createNormalizedNodeWriter(context, stack.toInference(), jsonWriter, depth, fields);
            final Module module = context.getSchemaContext().findModule(data.name().getNodeType().getModule())
                .orElseThrow();
            jsonWriter.name(module.getName() + ":output");
            jsonWriter.beginObject();
            writeChildren(nnWriter, (ContainerNode) data);
            jsonWriter.endObject();
        } else {
            final var stack = context.inference().toSchemaInferenceStack();
            if (!stack.isEmpty()) {
                stack.exit();
            }
            nnWriter = createNormalizedNodeWriter(context, stack.toInference(), jsonWriter, depth, fields);

            if (data instanceof MapEntryNode mapEntry) {
                // Restconf allows returning one list item. We need to wrap it
                // in map node in order to serialize it properly
                nnWriter.write(ImmutableNodes.mapNodeBuilder(data.name().getNodeType()).withChild(mapEntry).build());
            } else {
                nnWriter.write(data);
            }
        }

        nnWriter.flush();
    }

    private static void writeChildren(final RestconfNormalizedNodeWriter nnWriter, final ContainerNode data)
            throws IOException {
        for (var child : data.body()) {
            nnWriter.write(child);
        }
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(
            final InstanceIdentifierContext context, final Inference inference, final JsonWriter jsonWriter,
            final DepthParam depth, final List<Set<QName>> fields) {

        final SchemaNode schema = context.getSchemaNode();
        final JSONCodecFactory codecs = getCodecFactory(context);

        final NormalizedNodeStreamWriter streamWriter = JSONNormalizedNodeStreamWriter.createNestedWriter(
                codecs, inference, initialNamespaceFor(schema), jsonWriter);

        return ParameterAwareNormalizedNodeWriter.forStreamWriter(streamWriter, depth, fields);
    }

    private static XMLNamespace initialNamespaceFor(final SchemaNode schema) {
        if (schema instanceof RpcDefinition) {
            return schema.getQName().getNamespace();
        }
        // For top-level elements we always want to use namespace prefix, hence use a null initial namespace
        return null;
    }

    private static JsonWriter createJsonWriter(final OutputStream entityStream, final boolean prettyPrint) {
        if (prettyPrint) {
            return JsonWriterFactory.createJsonWriter(
                    new OutputStreamWriter(entityStream, StandardCharsets.UTF_8), DEFAULT_INDENT_SPACES_NUM);
        }
        return JsonWriterFactory.createJsonWriter(new OutputStreamWriter(entityStream, StandardCharsets.UTF_8));
    }

    private static JSONCodecFactory getCodecFactory(final InstanceIdentifierContext context) {
        // TODO: Performance: Cache JSON Codec factory and schema context
        return JSONCodecFactorySupplier.RFC7951.getShared(context.getSchemaContext());
    }
}
