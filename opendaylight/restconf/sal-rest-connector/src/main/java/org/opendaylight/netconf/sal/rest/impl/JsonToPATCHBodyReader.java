/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
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
import org.opendaylight.netconf.sal.restconf.impl.PATCHEditOperation;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
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

    private List<PATCHEntity> read(final JsonReader in, InstanceIdentifierContext path) throws IOException {
        List<PATCHEntity> resultCollection = new ArrayList<>();
        StringModuleInstanceIdentifierCodec codec = new StringModuleInstanceIdentifierCodec(path.getSchemaContext());
        PatchEdit edit = new PatchEdit();

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
                    in.beginObject();
                    break;
                case END_DOCUMENT:
                    break;
                case NAME:
                    final String name = in.nextName();
                    switch (name) {
                        case "edit" :
                            if (in.peek() == JsonToken.BEGIN_ARRAY) {
                                in.beginArray();

                                while (in.hasNext()) {
                                    readEditDefinition(edit, in, path, codec);
                                    resultCollection.add(prepareEditOperation(edit));
                                    edit.clear();
                                }

                                in.endArray();
                            } else {
                                readEditDefinition(edit, in, path, codec);
                                resultCollection.add(prepareEditOperation(edit));
                                edit.clear();
                            }

                            break;
                        case "patch-id" : this.patchId = in.nextString();
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

    /**
     * Read one patch edit object from Json input
     * @param edit PatchEdit instance to be filled with read data
     * @param in JsonReader reader
     * @param path InstanceIdentifierContext path context
     * @param codec StringModuleInstanceIdentifierCodec codec
     * @throws IOException
     */
    private void readEditDefinition(@Nonnull final PatchEdit edit, @Nonnull final JsonReader in,
                                    @Nonnull final InstanceIdentifierContext path,
                                    @Nonnull final StringModuleInstanceIdentifierCodec codec) throws IOException {
        StringBuffer value = new StringBuffer();
        in.beginObject();

        while (in.hasNext()) {
            final String editDefinition = in.nextName();
            switch (editDefinition) {
                case "edit-id" :
                    edit.id = in.nextString();
                    break;
                case "operation" :
                    edit.operation = in.nextString();
                    break;
                case "target" :
                    edit.target = codec.deserialize(codec.serialize(path.getInstanceIdentifier()) + in.nextString());
                    edit.targetSchemaNode = SchemaContextUtil.findDataSchemaNode(path.getSchemaContext(),
                            codec.getDataContextTree().getChild(edit.target).getDataSchemaNode().getPath().getParent());
                    break;
                case "value" :
                    // save data defined in value node for next (later) processing, because target needs to be read
                    // always first and there is no ordering in Json input
                    readValueNode(value, in);
                    break;
                default:
                    break;
            }
        }

        in.endObject();

        // read saved data to normalized node when target schema is already known
        edit.data = readEditData(new JsonReader(new StringReader(value.toString())), edit.targetSchemaNode, path);
    }

    /**
     * Parse data defined in value node and saves it to buffer
     * @param value Buffer to read value node
     * @param in JsonReader reader
     * @throws IOException
     */
    private void readValueNode(@Nonnull final StringBuffer value, @Nonnull final JsonReader in) throws IOException {
        in.beginObject();
        value.append("{");

        value.append("\"" + in.nextName() + "\"" + ":");

        if (in.peek() == JsonToken.BEGIN_ARRAY) {
            in.beginArray();
            value.append("[");

            while (in.hasNext()) {
                readValueObject(value, in);
                if (in.peek() != JsonToken.END_ARRAY) {
                    value.append(",");
                }
            }

            in.endArray();
            value.append("]");
        } else {
            readValueObject(value, in);
        }

        in.endObject();
        value.append("}");
    }

    /**
     * Parse one value object of data and saves it to buffer
     * @param value Buffer to read value object
     * @param in JsonReader reader
     * @throws IOException
     */
    private void readValueObject(@Nonnull final StringBuffer value, @Nonnull final JsonReader in) throws IOException {
        in.beginObject();
        value.append("{");

        while (in.hasNext()) {
            value.append("\"" + in.nextName() + "\"");
            value.append(":");
            value.append("\"" + in.nextString() + "\"");
            if (in.peek() != JsonToken.END_OBJECT) {
                value.append(",");
            }
        }

        in.endObject();
        value.append("}");
    }

    /**
     * Read patch edit data defined in value node to NormalizedNode
     * @param in reader JsonReader reader
     * @return NormalizedNode representing data
     */
    private NormalizedNode readEditData(@Nonnull final JsonReader in, @Nonnull SchemaNode targetSchemaNode,
                                        @Nonnull InstanceIdentifierContext path) {
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        JsonParserStream.create(writer, path.getSchemaContext(), targetSchemaNode).parse(in);

        return resultHolder.getResult();
    }

    /**
     * Prepare PATCHEntity from PatchEdit instance when it satisfies conditions, otherwise throws exception
     * @param edit Instance of PatchEdit
     * @return PATCHEntity
     */
    private PATCHEntity prepareEditOperation(@Nonnull final PatchEdit edit) {
        if (edit.operation != null && edit.targetSchemaNode != null
                && checkDataPresence(edit.operation, (edit.data != null))) {
            if (isPatchOperationWithValue(edit.operation)) {
                return new PATCHEntity(edit.id, edit.operation, edit.target.getParent(), edit.data);
            } else {
                return new PATCHEntity(edit.id, edit.operation, edit.target);
            }
        }

        throw new RestconfDocumentedException("Error parsing input", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
    }

    /**
     * Check if data is present when operation requires it and not present when operation data is not allowed
     * @param operation Name of operation
     * @param hasData Data in edit are present/not present
     * @return true if data is present when operation requires it or if there are no data when operation does not
     * allow it, false otherwise
     */
    private boolean checkDataPresence(@Nonnull final String operation, final boolean hasData) {
        if (isPatchOperationWithValue(operation)) {
            if (hasData) {
                return true;
            } else {
                return false;
            }
        } else  {
            if (!hasData) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Check if operation requires data to be specified
     * @param operation Name of the operation to be checked
     * @return true if operation requires data, false otherwise
     */
    private boolean isPatchOperationWithValue(@Nonnull final String operation) {
        switch (PATCHEditOperation.valueOf(operation.toUpperCase())) {
            case CREATE:
            case MERGE:
            case REPLACE:
            case INSERT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Helper class representing one patch edit
     */
    private final class PatchEdit {
        String id;
        String operation;
        YangInstanceIdentifier target;
        SchemaNode targetSchemaNode;
        NormalizedNode data;

        void clear() {
            id = null;
            operation = null;
            target = null;
            targetSchemaNode = null;
            data = null;
        }
    }
}
