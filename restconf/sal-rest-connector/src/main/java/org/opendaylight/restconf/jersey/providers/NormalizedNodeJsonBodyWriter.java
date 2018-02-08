/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.jersey.providers;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import org.opendaylight.netconf.sal.rest.api.RestconfNormalizedNodeWriter;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.Rfc8040;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
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
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

@Provider
@Produces({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, MediaType.APPLICATION_JSON })
public class NormalizedNodeJsonBodyWriter implements MessageBodyWriter<NormalizedNodeContext> {

    private static final int DEFAULT_INDENT_SPACES_NUM = 2;

    @Override
    public boolean isWriteable(final Class<?> type,
                               final Type genericType,
                               final Annotation[] annotations,
                               final MediaType mediaType) {
        return type.equals(NormalizedNodeContext.class);
    }

    @Override
    public long getSize(final NormalizedNodeContext context,
                        final Class<?> type,
                        final Type genericType,
                        final Annotation[] annotations,
                        final MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(final NormalizedNodeContext context,
                        final Class<?> type,
                        final Type genericType,
                        final Annotation[] annotations,
                        final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws IOException, WebApplicationException {
        final NormalizedNode<?, ?> data = context.getData();
        if (data == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        final InstanceIdentifierContext<SchemaNode> identifierCtx =
                (InstanceIdentifierContext<SchemaNode>) context.getInstanceIdentifierContext();
        final SchemaPath path = identifierCtx.getSchemaNode().getPath();

        try (JsonWriter jsonWriter = createJsonWriter(entityStream, context.getWriterParameters().isPrettyPrint())) {
            jsonWriter.beginObject();
            writeNormalizedNode(jsonWriter, path, identifierCtx, data,
                    context.getWriterParameters().getDepth(), context.getWriterParameters().getFields());
            jsonWriter.endObject();
            jsonWriter.flush();
        }
    }

    private static void writeNormalizedNode(final JsonWriter jsonWriter,
            final SchemaPath path, final InstanceIdentifierContext<SchemaNode> context, final NormalizedNode<?, ?> data,
            final Integer depth, final List<Set<QName>> fields) throws IOException {
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
            jsonWriter.name("output");
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
                nnWriter.write(
                        ImmutableNodes.mapNodeBuilder(data.getNodeType()).withChild((MapEntryNode) data).build());
            } else {
                nnWriter.write(data);
            }
        }

        nnWriter.flush();
    }

    private static void writeChildren(final RestconfNormalizedNodeWriter nnWriter,
                                      final ContainerNode data) throws IOException {
        for (final DataContainerChild<? extends PathArgument, ?> child : data.getValue()) {
            nnWriter.write(child);
        }
    }

    private static RestconfNormalizedNodeWriter createNormalizedNodeWriter(
            final InstanceIdentifierContext<SchemaNode> context, final SchemaPath path, final JsonWriter jsonWriter,
            final Integer depth, final List<Set<QName>> fields) {

        final SchemaNode schema = context.getSchemaNode();
        final JSONCodecFactory codecs = getCodecFactory(context);

        final URI initialNs;
        if (schema instanceof DataSchemaNode
                && !((DataSchemaNode)schema).isAugmenting()
                && !(schema instanceof SchemaContext)) {
            initialNs = schema.getQName().getNamespace();
        } else if (schema instanceof RpcDefinition) {
            initialNs = schema.getQName().getNamespace();
        } else {
            initialNs = null;
        }
        final NormalizedNodeStreamWriter streamWriter = JSONNormalizedNodeStreamWriter.createNestedWriter(
                codecs, path, initialNs, jsonWriter);
        return ParameterAwareNormalizedNodeWriter.forStreamWriter(streamWriter, depth, fields);
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
