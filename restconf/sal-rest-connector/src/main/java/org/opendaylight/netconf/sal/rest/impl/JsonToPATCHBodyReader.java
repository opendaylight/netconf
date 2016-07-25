/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
import org.opendaylight.yangtools.yang.data.util.AbstractModuleStringInstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Consumes({Draft02.MediaTypes.PATCH + RestconfService.JSON})
public class JsonToPATCHBodyReader extends AbstractIdentifierAwareJaxRsProvider implements MessageBodyReader<PATCHContext> {

    private final static Logger LOG = LoggerFactory.getLogger(JsonToPATCHBodyReader.class);
    private String patchId;

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    @Override
    public PATCHContext readFrom(Class<PATCHContext> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        try {
            return readFrom(getInstanceIdentifierContext(), entityStream);
        } catch (final Exception e) {
            throw propagateExceptionAs(e);
        }
    }

    private static RuntimeException propagateExceptionAs(Exception e) throws RestconfDocumentedException {
        if(e instanceof RestconfDocumentedException) {
            throw (RestconfDocumentedException)e;
        }

        if(e instanceof ResultAlreadySetException) {
            LOG.debug("Error parsing json input:", e);

            throw new RestconfDocumentedException("Error parsing json input: Failed to create new parse result data. ");
        }

        throw new RestconfDocumentedException("Error parsing json input: " + e.getMessage(), ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, e);
    }

    public PATCHContext readFrom(final String uriPath, final InputStream entityStream) throws
            RestconfDocumentedException {
        try {
            return readFrom(ControllerContext.getInstance().toInstanceIdentifier(uriPath), entityStream);
        } catch (final Exception e) {
            propagateExceptionAs(e);
            return null; // no-op
        }
    }

    private PATCHContext readFrom(final InstanceIdentifierContext<?> path, final InputStream entityStream) throws IOException {
        if (entityStream.available() < 1) {
            return new PATCHContext(path, null, null);
        }

        final JsonReader jsonReader = new JsonReader(new InputStreamReader(entityStream));
        final List<PATCHEntity> resultList = read(jsonReader, path);
        jsonReader.close();

        return new PATCHContext(path, resultList, patchId);
    }

    private List<PATCHEntity> read(final JsonReader in, InstanceIdentifierContext path) throws
            IOException {

        boolean inEdit = false;
        boolean inValue = false;
        String operation = null;
        String target = null;
        String editId = null;
        List<PATCHEntity> resultCollection = new ArrayList<>();

        while (in.hasNext()) {
            switch (in.peek()) {
                case STRING:
                case NUMBER:
                    in.nextString();
                    break;
                case BOOLEAN:
                    Boolean.toString(in.nextBoolean());
                    break;
                case NULL:
                    in.nextNull();
                    break;
                case BEGIN_ARRAY:
                    in.beginArray();
                    break;
                case BEGIN_OBJECT:
                    if (inEdit && operation != null & target != null & inValue) {
                        StringModuleInstanceIdentifierCodec codec = new StringModuleInstanceIdentifierCodec(path
                              .getSchemaContext());

                        YangInstanceIdentifier targetII = codec.deserialize(codec.serialize(path
                                .getInstanceIdentifier()) + target);
                        SchemaNode targetSchemaNode = SchemaContextUtil.findDataSchemaNode(path.getSchemaContext(),
                                codec.getDataContextTree().getChild(targetII).getDataSchemaNode().getPath()
                                        .getParent());

                        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
                        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

                        //keep on parsing json from place where target points
                        final JsonParserStream jsonParser = JsonParserStream.create(writer, path.getSchemaContext(),
                                targetSchemaNode);
                        jsonParser.parse(in);
                        resultCollection.add(new PATCHEntity(editId, operation, targetII.getParent(), resultHolder.getResult()));
                        inValue = false;

                        operation = null;
                        target = null;
                        in.endObject();
                    } else {
                        in.beginObject();
                    }
                    break;
                case END_DOCUMENT:
                    break;
                case NAME:
                    final String name = in.nextName();

                    switch (name) {
                        case "edit" : inEdit = true;
                            break;
                        case "operation" : operation = in.nextString();
                            break;
                        case "target" : target = in.nextString();
                            break;
                        case "value" : inValue = true;
                            break;
                        case "patch-id" : patchId = in.nextString();
                            break;
                        case "edit-id" : editId = in.nextString();
                            break;
                    }
                    break;
                case END_OBJECT:
                    in.endObject();
                    break;
            case END_ARRAY:
                in.endArray();
                break;

            default:
                break;
            }
        }

        return ImmutableList.copyOf(resultCollection);
    }

    private class StringModuleInstanceIdentifierCodec extends AbstractModuleStringInstanceIdentifierCodec {

        private final DataSchemaContextTree dataContextTree;
        private final SchemaContext context;

        StringModuleInstanceIdentifierCodec(SchemaContext context) {
            this.context = Preconditions.checkNotNull(context);
            this.dataContextTree = DataSchemaContextTree.from(context);
        }

        @Override
        protected Module moduleForPrefix(@Nonnull String prefix) {
            return context.findModuleByName(prefix, null);
        }

        @Nonnull
        @Override
        protected DataSchemaContextTree getDataContextTree() {
            return dataContextTree;
        }

        @Nullable
        @Override
        protected String prefixForNamespace(@Nonnull URI namespace) {
            final Module module = context.findModuleByNamespaceAndRevision(namespace, null);
            return module == null ? null : module.getName();
        }
    }
}
