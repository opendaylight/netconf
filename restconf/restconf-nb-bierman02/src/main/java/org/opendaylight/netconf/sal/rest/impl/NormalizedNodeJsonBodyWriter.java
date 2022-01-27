/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.impl;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.stream.XMLStreamException;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.RestconfNormalizedNodeWriter;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
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
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.xml.sax.SAXException;

/**
 * Normalized node writer for JSON.
 *
 * @deprecated This class will be replaced by NormalizedNodeJsonBodyWriter from restconf-nb-rfc8040
 */
@Deprecated
@Provider
@Produces({
    Draft02.MediaTypes.API + RestconfService.JSON,
    Draft02.MediaTypes.DATA + RestconfService.JSON,
    Draft02.MediaTypes.OPERATION + RestconfService.JSON,
    MediaType.APPLICATION_JSON
})
public class NormalizedNodeJsonBodyWriter implements MessageBodyWriter<NormalizedNodeContext> {

    private static final int DEFAULT_INDENT_SPACES_NUM = 2;

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return type.equals(NormalizedNodeContext.class);
    }

    @Override
    public long getSize(final NormalizedNodeContext context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(final NormalizedNodeContext context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
            final OutputStream entityStream) throws IOException, WebApplicationException {
        if (httpHeaders != null) {
            for (final Entry<String, Object> entry : context.getNewHeaders().entrySet()) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }
        }
        NormalizedNode data = context.getData();
        if (data == null) {
            return;
        }

        final InstanceIdentifierContext identifierCtx = context.getInstanceIdentifierContext();

        try (JsonWriter jsonWriter = createJsonWriter(entityStream, context.getWriterParameters().isPrettyPrint())) {
            jsonWriter.beginObject();
            writeNormalizedNode(jsonWriter, identifierCtx, data, context.getWriterParameters().getDepth());
            jsonWriter.endObject();
            jsonWriter.flush();
        }
    }

    private static void writeNormalizedNode(final JsonWriter jsonWriter, final InstanceIdentifierContext context,
            // Note: mutable argument
            NormalizedNode data, final @Nullable Integer depth) throws IOException {

        final var stack = context.inference().toSchemaInferenceStack();
        final RestconfNormalizedNodeWriter nnWriter;
        if (stack.isEmpty()) {
            /*
             *  Creates writer without initialNs and we write children of root data container
             *  which is not visible in restconf
             */
            nnWriter = createNormalizedNodeWriter(context, context.inference(), jsonWriter, depth);
            if (data instanceof ContainerNode) {
                writeChildren(nnWriter,(ContainerNode) data);
            } else if (data instanceof DOMSourceAnyxmlNode) {
                try {
                    writeChildren(nnWriter,
                            (ContainerNode) NetconfUtil.transformDOMSourceToNormalizedNode(
                                    context.getSchemaContext(), ((DOMSourceAnyxmlNode)data).body()).getResult());
                } catch (XMLStreamException | URISyntaxException | SAXException e) {
                    throw new IOException("Cannot write anyxml.", e);
                }
            }
        } else if (context.getSchemaNode() instanceof RpcDefinition) {
            /*
             *  RpcDefinition is not supported as initial codec in JSONStreamWriter,
             *  so we need to emit initial output declaratation..
             */
            final var rpc = (RpcDefinition) context.getSchemaNode();
            final var tmp = SchemaInferenceStack.of(context.getSchemaContext());
            tmp.enterSchemaTree(rpc.getQName());
            tmp.enterSchemaTree(rpc.getOutput().getQName());

            nnWriter = createNormalizedNodeWriter(context, tmp.toInference(), jsonWriter, depth);
            jsonWriter.name("output");
            jsonWriter.beginObject();
            writeChildren(nnWriter, (ContainerNode) data);
            jsonWriter.endObject();
        } else {
            stack.exit();

            if (data instanceof MapEntryNode) {
                data = ImmutableNodes.mapNodeBuilder(data.getIdentifier().getNodeType())
                    .withChild((MapEntryNode) data)
                    .build();
            }
            nnWriter = createNormalizedNodeWriter(context, stack.toInference(), jsonWriter, depth);
            nnWriter.write(data);
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
            final InstanceIdentifierContext context, final Inference inference, final JsonWriter jsonWriter,
            final @Nullable Integer depth) {

        final SchemaNode schema = context.getSchemaNode();
        final JSONCodecFactory codecs = getCodecFactory(context);

        final XMLNamespace initialNs;
        if (schema instanceof DataSchemaNode && !((DataSchemaNode)schema).isAugmenting()
                && !(schema instanceof SchemaContext) || schema instanceof RpcDefinition) {
            initialNs = schema.getQName().getNamespace();
        } else {
            initialNs = null;
        }
        final NormalizedNodeStreamWriter streamWriter =
                JSONNormalizedNodeStreamWriter.createNestedWriter(codecs, inference, initialNs, jsonWriter);
        if (depth != null) {
            return DepthAwareNormalizedNodeWriter.forStreamWriter(streamWriter, depth);
        }

        return RestconfDelegatingNormalizedNodeWriter.forStreamWriter(streamWriter);
    }

    private static JsonWriter createJsonWriter(final OutputStream entityStream, final boolean prettyPrint) {
        if (prettyPrint) {
            return JsonWriterFactory.createJsonWriter(new OutputStreamWriter(entityStream, StandardCharsets.UTF_8),
                    DEFAULT_INDENT_SPACES_NUM);
        }

        return JsonWriterFactory.createJsonWriter(new OutputStreamWriter(entityStream, StandardCharsets.UTF_8));
    }

    private static JSONCodecFactory getCodecFactory(final InstanceIdentifierContext context) {
        // TODO: Performance: Cache JSON Codec factory and schema context
        return JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02.getShared(context.getSchemaContext());
    }
}
