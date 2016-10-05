/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import static org.opendaylight.netconf.sal.restconf.impl.PATCHEditOperation.isPatchOperationWithValue;

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
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
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

/**
 * @deprecated This class will be replaced by
 *             {@link org.opendaylight.restconf.jersey.providers.JsonToPATCHBodyReader}
 */
@Deprecated
@Provider
@Consumes({Draft02.MediaTypes.PATCH + RestconfService.JSON})
public class JsonToPATCHBodyReader extends AbstractIdentifierAwareJaxRsProvider
        implements MessageBodyReader<PATCHContext> {

    private final static Logger LOG = LoggerFactory.getLogger(JsonToPATCHBodyReader.class);
    private String patchId;

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType,
                              final Annotation[] annotations, final MediaType mediaType) {
        return true;
    }

    @Override
    public PATCHContext readFrom(final Class<PATCHContext> type, final Type genericType,
                                 final Annotation[] annotations, final MediaType mediaType,
                                 final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream)
            throws IOException, WebApplicationException {
        try {
            return readFrom(getInstanceIdentifierContext(), entityStream);
        } catch (final Exception e) {
            throw propagateExceptionAs(e);
        }
    }

    private static RuntimeException propagateExceptionAs(final Exception e) throws RestconfDocumentedException {
        if (e instanceof RestconfDocumentedException) {
            throw (RestconfDocumentedException)e;
        }

        if (e instanceof ResultAlreadySetException) {
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

    private PATCHContext readFrom(final InstanceIdentifierContext<?> path, final InputStream entityStream)
            throws IOException {
        if (entityStream.available() < 1) {
            return new PATCHContext(path, null, null);
        }

        final JsonReader jsonReader = new JsonReader(new InputStreamReader(entityStream));
        final List<PATCHEntity> resultList = read(jsonReader, path);
        jsonReader.close();

        return new PATCHContext(path, resultList, this.patchId);
    }

    private List<PATCHEntity> read(final JsonReader in, final InstanceIdentifierContext path) throws IOException {
        final List<PATCHEntity> resultCollection = new ArrayList<>();
        final StringModuleInstanceIdentifierCodec codec = new StringModuleInstanceIdentifierCodec(
                path.getSchemaContext());
        final JsonToPATCHBodyReader.PatchEdit edit = new JsonToPATCHBodyReader.PatchEdit();

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
                    parseByName(in.nextName(), edit, in, path, codec, resultCollection);
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
     * Switch value of parsed JsonToken.NAME and read edit definition or patch id
     * @param name value of token
     * @param edit PatchEdit instance
     * @param in JsonReader reader
     * @param path InstanceIdentifierContext context
     * @param codec StringModuleInstanceIdentifierCodec codec
     * @param resultCollection collection of parsed edits
     * @throws IOException
     */
    private void parseByName(@Nonnull final String name, @Nonnull final PatchEdit edit,
                             @Nonnull final JsonReader in, @Nonnull final InstanceIdentifierContext path,
                             @Nonnull final StringModuleInstanceIdentifierCodec codec,
                             @Nonnull final List<PATCHEntity> resultCollection) throws IOException {
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
            case "patch-id" :
                this.patchId = in.nextString();
                break;
            default:
                break;
        }
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
        final StringBuffer value = new StringBuffer();
        in.beginObject();

        while (in.hasNext()) {
            final String editDefinition = in.nextName();
            switch (editDefinition) {
                case "edit-id" :
                    edit.setId(in.nextString());
                    break;
                case "operation" :
                    edit.setOperation(in.nextString());
                    break;
                case "target" :
                    // target can be specified completely in request URI
                    final String target = in.nextString();
                    if (target.equals("/")) {
                        edit.setTarget(path.getInstanceIdentifier());
                        edit.setTargetSchemaNode(path.getSchemaContext());
                    } else {
                        edit.setTarget(codec.deserialize(codec.serialize(path.getInstanceIdentifier()).concat(target)));
                        edit.setTargetSchemaNode(SchemaContextUtil.findDataSchemaNode(path.getSchemaContext(),
                                codec.getDataContextTree().getChild(edit.getTarget()).getDataSchemaNode().getPath()
                                        .getParent()));
                    }

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
        edit.setData(readEditData(new JsonReader(new StringReader(value.toString())), edit.getTargetSchemaNode(), path));
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

            if (in.peek() == JsonToken.STRING) {
                value.append("\"" + in.nextString() + "\"");
            } else {
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
            }

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
    private NormalizedNode readEditData(@Nonnull final JsonReader in, @Nonnull final SchemaNode targetSchemaNode,
                                        @Nonnull final InstanceIdentifierContext path) {
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
        if ((edit.getOperation() != null) && (edit.getTargetSchemaNode() != null)
                && checkDataPresence(edit.getOperation(), (edit.getData() != null))) {
            if (isPatchOperationWithValue(edit.getOperation())) {
                // for lists allow to manipulate with list items through their parent
                final YangInstanceIdentifier targetNode;
                if (edit.getTarget().getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                    targetNode = edit.getTarget().getParent();
                } else {
                    targetNode = edit.getTarget();
                }

                return new PATCHEntity(edit.getId(), edit.getOperation(), targetNode, edit.getData());
            } else {
                return new PATCHEntity(edit.getId(), edit.getOperation(), edit.getTarget());
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
     * Helper class representing one patch edit
     */
    private static final class PatchEdit {
        private String id;
        private String operation;
        private YangInstanceIdentifier target;
        private SchemaNode targetSchemaNode;
        private NormalizedNode data;

        public String getId() {
            return this.id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        public String getOperation() {
            return this.operation;
        }

        public void setOperation(final String operation) {
            this.operation = operation;
        }

        public YangInstanceIdentifier getTarget() {
            return this.target;
        }

        public void setTarget(final YangInstanceIdentifier target) {
            this.target = target;
        }

        public SchemaNode getTargetSchemaNode() {
            return this.targetSchemaNode;
        }

        public void setTargetSchemaNode(final SchemaNode targetSchemaNode) {
            this.targetSchemaNode = targetSchemaNode;
        }

        public NormalizedNode getData() {
            return this.data;
        }

        public void setData(final NormalizedNode data) {
            this.data = data;
        }

        public void clear() {
            this.id = null;
            this.operation = null;
            this.target = null;
            this.targetSchemaNode = null;
            this.data = null;
        }
    }
}
