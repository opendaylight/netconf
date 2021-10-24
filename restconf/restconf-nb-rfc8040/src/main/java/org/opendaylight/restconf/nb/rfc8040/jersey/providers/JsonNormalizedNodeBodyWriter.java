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
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.DepthParam;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
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
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

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

        @SuppressWarnings("unchecked")
        final InstanceIdentifierContext<SchemaNode> identifierCtx =
                (InstanceIdentifierContext<SchemaNode>) context.getInstanceIdentifierContext();
        final SchemaPath path = identifierCtx.getSchemaNode().getPath();

        try (JsonWriter jsonWriter = createJsonWriter(entityStream, context.getWriterParameters().prettyPrint())) {
            jsonWriter.beginObject();
            writeNormalizedNode(jsonWriter, path, identifierCtx, data,
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

    private static void writeNormalizedNode(final JsonWriter jsonWriter,
            final SchemaPath path, final InstanceIdentifierContext<SchemaNode> context, final NormalizedNode data,
            final DepthParam depth, final List<Set<QName>> fields) throws IOException {
        final RestconfNormalizedNodeWriter nnWriter;

        if (context.getSchemaNode() instanceof RpcDefinition) {
            /*
             *  RpcDefinition is not supported as initial codec in JSONStreamWriter,
             *  so we need to emit initial output declaration..
             */
            nnWriter = createNormalizedNodeWriter(
                    context,
                    ((RpcDefinition) context.getSchemaNode()).getOutput().getPath(),
                    jsonWriter,
                    depth,
                    fields);
            final Module module = context.getSchemaContext().findModule(data.getIdentifier().getNodeType().getModule())
                .get();
            jsonWriter.name(module.getName() + ":output");
            jsonWriter.beginObject();
            writeChildren(nnWriter, (ContainerNode) data);
            jsonWriter.endObject();
        } else if (context.getSchemaNode() instanceof ActionDefinition) {
            /*
             *  ActionDefinition is not supported as initial codec in JSONStreamWriter,
             *  so we need to emit initial output declaration..
             */
            nnWriter = createNormalizedNodeWriter(context,
                ((ActionDefinition) context.getSchemaNode()).getOutput().getPath(), jsonWriter, depth, fields);
            final Module module = context.getSchemaContext().findModule(data.getIdentifier().getNodeType().getModule())
                .get();
            jsonWriter.name(module.getName() + ":output");
            jsonWriter.beginObject();
            writeChildren(nnWriter, (ContainerNode) data);
            jsonWriter.endObject();
        } else {
            if (SchemaPath.ROOT.equals(path)) {
                nnWriter = createNormalizedNodeWriter(context, path, jsonWriter, depth, fields);
            } else {
                nnWriter = createNormalizedNodeWriter(context, path.getParent(), jsonWriter, depth, fields);
            }

            if (data instanceof MapEntryNode) {
                // Restconf allows returning one list item. We need to wrap it
                // in map node in order to serialize it properly
                nnWriter.write(ImmutableNodes.mapNodeBuilder(data.getIdentifier().getNodeType())
                    .withChild((MapEntryNode) data)
                    .build());
            } else {
                nnWriter.write(data);
            }
        }

        nnWriter.flush();
    }

    private static void writeChildren(final RestconfNormalizedNodeWriter nnWriter, final ContainerNode data)
            throws IOException {
        for (final DataContainerChild child : data.body()) {
            nnWriter.write(child);
        }
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(
            final InstanceIdentifierContext<SchemaNode> context, final SchemaPath path, final JsonWriter jsonWriter,
            final DepthParam depth, final List<Set<QName>> fields) {

        final SchemaNode schema = context.getSchemaNode();
        final JSONCodecFactory codecs = getCodecFactory(context);

        final NormalizedNodeStreamWriter streamWriter = JSONNormalizedNodeStreamWriter.createNestedWriter(
                codecs, path, initialNamespaceFor(schema), jsonWriter);

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

    private static JSONCodecFactory getCodecFactory(final InstanceIdentifierContext<?> context) {
        // TODO: Performance: Cache JSON Codec factory and schema context
        return JSONCodecFactorySupplier.RFC7951.getShared(context.getSchemaContext());
    }
}
