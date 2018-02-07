/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.jersey.providers;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.PatchContext;
import org.opendaylight.netconf.sal.restconf.impl.PatchEditOperation;
import org.opendaylight.netconf.sal.restconf.impl.PatchEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Rfc8040;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Consumes({Rfc8040.MediaTypes.PATCH + RestconfConstants.JSON})
public class JsonToPatchBodyReader extends AbstractToPatchBodyReader {
    private static final Logger LOG = LoggerFactory.getLogger(JsonToPatchBodyReader.class);

    private String patchId;

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected PatchContext readBody(final InstanceIdentifierContext<?> path, final InputStream entityStream)
            throws IOException, WebApplicationException {
        try {
            return readFrom(path, entityStream);
        } catch (final Exception e) {
            throw propagateExceptionAs(e);
        }
    }

    private PatchContext readFrom(final InstanceIdentifierContext<?> path, final InputStream entityStream)
            throws IOException {
        final JsonReader jsonReader = new JsonReader(new InputStreamReader(entityStream));
        final List<PatchEntity> resultList = read(jsonReader, path);
        jsonReader.close();

        return new PatchContext(path, resultList, patchId);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public PatchContext readFrom(final String uriPath, final InputStream entityStream) throws
            RestconfDocumentedException {
        try {
            return readFrom(ControllerContext.getInstance().toInstanceIdentifier(uriPath), entityStream);
        } catch (final Exception e) {
            propagateExceptionAs(e);
            return null; // no-op
        }
    }

    private static RuntimeException propagateExceptionAs(final Exception exception) throws RestconfDocumentedException {
        if (exception instanceof RestconfDocumentedException) {
            throw (RestconfDocumentedException)exception;
        }

        if (exception instanceof ResultAlreadySetException) {
            LOG.debug("Error parsing json input:", exception);
            throw new RestconfDocumentedException("Error parsing json input: Failed to create new parse result data. ");
        }

        throw new RestconfDocumentedException("Error parsing json input: " + exception.getMessage(), ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, exception);
    }

    private List<PatchEntity> read(final JsonReader in, final InstanceIdentifierContext<?> path) throws IOException {
        final List<PatchEntity> resultCollection = new ArrayList<>();
        final StringModuleInstanceIdentifierCodec codec = new StringModuleInstanceIdentifierCodec(
                path.getSchemaContext());
        final JsonToPatchBodyReader.PatchEdit edit = new JsonToPatchBodyReader.PatchEdit();

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
     * Switch value of parsed JsonToken.NAME and read edit definition or patch id.
     *
     * @param name value of token
     * @param edit PatchEdit instance
     * @param in JsonReader reader
     * @param path InstanceIdentifierContext context
     * @param codec Draft11StringModuleInstanceIdentifierCodec codec
     * @param resultCollection collection of parsed edits
     * @throws IOException if operation fails
     */
    private void parseByName(@Nonnull final String name, @Nonnull final PatchEdit edit,
                             @Nonnull final JsonReader in, @Nonnull final InstanceIdentifierContext<?> path,
                             @Nonnull final StringModuleInstanceIdentifierCodec codec,
                             @Nonnull final List<PatchEntity> resultCollection) throws IOException {
        switch (name) {
            case "edit":
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
            case "patch-id":
                this.patchId = in.nextString();
                break;
            default:
                break;
        }
    }

    /**
     * Read one patch edit object from Json input.
     *
     * @param edit PatchEdit instance to be filled with read data
     * @param in JsonReader reader
     * @param path InstanceIdentifierContext path context
     * @param codec Draft11StringModuleInstanceIdentifierCodec codec
     * @throws IOException if operation fails
     */
    private void readEditDefinition(@Nonnull final PatchEdit edit, @Nonnull final JsonReader in,
                                    @Nonnull final InstanceIdentifierContext<?> path,
                                    @Nonnull final StringModuleInstanceIdentifierCodec codec) throws IOException {
        String deferredValue = null;
        in.beginObject();

        while (in.hasNext()) {
            final String editDefinition = in.nextName();
            switch (editDefinition) {
                case "edit-id":
                    edit.setId(in.nextString());
                    break;
                case "operation":
                    edit.setOperation(PatchEditOperation.valueOf(in.nextString().toUpperCase()));
                    break;
                case "target":
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
                case "value":
                    Preconditions.checkArgument(edit.getData() == null && deferredValue == null,
                            "Multiple value entries found");

                    if (edit.getTargetSchemaNode() == null) {
                        final StringBuilder sb = new StringBuilder();

                        // save data defined in value node for next (later) processing, because target needs to be read
                        // always first and there is no ordering in Json input
                        readValueNode(sb, in);
                        deferredValue = sb.toString();
                    } else {
                        // We have a target schema node, reuse this reader without buffering the value.
                        edit.setData(readEditData(in, edit.getTargetSchemaNode(), path));
                    }
                    break;
                default:
                    // FIXME: this does not look right, as it can wreck our logic
                    break;
            }
        }

        in.endObject();

        if (deferredValue != null) {
            // read saved data to normalized node when target schema is already known
            edit.setData(readEditData(new JsonReader(new StringReader(deferredValue)), edit.getTargetSchemaNode(),
                path));
        }
    }

    /**
     * Parse data defined in value node and saves it to buffer.
     * @param sb Buffer to read value node
     * @param in JsonReader reader
     * @throws IOException if operation fails
     */
    private void readValueNode(@Nonnull final StringBuilder sb, @Nonnull final JsonReader in) throws IOException {
        in.beginObject();

        sb.append("{\"").append(in.nextName()).append("\":");

        switch (in.peek()) {
            case BEGIN_ARRAY:
                in.beginArray();
                sb.append('[');

                while (in.hasNext()) {
                    if (in.peek() == JsonToken.STRING) {
                        sb.append('"').append(in.nextString()).append('"');
                    } else {
                        readValueObject(sb, in);
                    }
                    if (in.peek() != JsonToken.END_ARRAY) {
                        sb.append(',');
                    }
                }

                in.endArray();
                sb.append(']');
                break;
            default:
                readValueObject(sb, in);
                break;
        }

        in.endObject();
        sb.append('}');
    }

    /**
     * Parse one value object of data and saves it to buffer.
     * @param sb Buffer to read value object
     * @param in JsonReader reader
     * @throws IOException if operation fails
     */
    private void readValueObject(@Nonnull final StringBuilder sb, @Nonnull final JsonReader in) throws IOException {
        // read simple leaf value
        if (in.peek() == JsonToken.STRING) {
            sb.append('"').append(in.nextString()).append('"');
            return;
        }

        in.beginObject();
        sb.append('{');

        while (in.hasNext()) {
            sb.append('"').append(in.nextName()).append("\":");

            switch (in.peek()) {
                case STRING:
                    sb.append('"').append(in.nextString()).append('"');
                    break;
                case BEGIN_ARRAY:
                    in.beginArray();
                    sb.append('[');

                    while (in.hasNext()) {
                        if (in.peek() == JsonToken.STRING) {
                            sb.append('"').append(in.nextString()).append('"');
                        } else {
                            readValueObject(sb, in);
                        }

                        if (in.peek() != JsonToken.END_ARRAY) {
                            sb.append(',');
                        }
                    }

                    in.endArray();
                    sb.append(']');
                    break;
                default:
                    readValueObject(sb, in);
            }

            if (in.peek() != JsonToken.END_OBJECT) {
                sb.append(',');
            }
        }

        in.endObject();
        sb.append('}');
    }

    /**
     * Read patch edit data defined in value node to NormalizedNode.
     * @param in reader JsonReader reader
     * @return NormalizedNode representing data
     */
    private static NormalizedNode<?, ?> readEditData(@Nonnull final JsonReader in,
            @Nonnull final SchemaNode targetSchemaNode, @Nonnull final InstanceIdentifierContext<?> path) {
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        JsonParserStream.create(writer, JSONCodecFactorySupplier.RFC7951.getShared(path.getSchemaContext()),
            targetSchemaNode).parse(in);

        return resultHolder.getResult();
    }

    /**
     * Prepare PatchEntity from PatchEdit instance when it satisfies conditions, otherwise throws exception.
     * @param edit Instance of PatchEdit
     * @return PatchEntity Patch entity
     */
    private static PatchEntity prepareEditOperation(@Nonnull final PatchEdit edit) {
        if (edit.getOperation() != null && edit.getTargetSchemaNode() != null
                && checkDataPresence(edit.getOperation(), edit.getData() != null)) {
            if (!edit.getOperation().isWithValue()) {
                return new PatchEntity(edit.getId(), edit.getOperation(), edit.getTarget());
            }

            // for lists allow to manipulate with list items through their parent
            final YangInstanceIdentifier targetNode;
            if (edit.getTarget().getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                targetNode = edit.getTarget().getParent();
            } else {
                targetNode = edit.getTarget();
            }

            return new PatchEntity(edit.getId(), edit.getOperation(), targetNode, edit.getData());
        }

        throw new RestconfDocumentedException("Error parsing input", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
    }

    /**
     * Check if data is present when operation requires it and not present when operation data is not allowed.
     * @param operation Name of operation
     * @param hasData Data in edit are present/not present
     * @return true if data is present when operation requires it or if there are no data when operation does not
     *     allow it, false otherwise
     */
    private static boolean checkDataPresence(@Nonnull final PatchEditOperation operation, final boolean hasData) {
        return operation.isWithValue() == hasData;
    }

    /**
     * Helper class representing one patch edit.
     */
    private static final class PatchEdit {
        private String id;
        private PatchEditOperation operation;
        private YangInstanceIdentifier target;
        private SchemaNode targetSchemaNode;
        private NormalizedNode<?, ?> data;

        String getId() {
            return id;
        }

        void setId(final String id) {
            this.id = Preconditions.checkNotNull(id);
        }

        PatchEditOperation getOperation() {
            return operation;
        }

        void setOperation(final PatchEditOperation operation) {
            this.operation = Preconditions.checkNotNull(operation);
        }

        YangInstanceIdentifier getTarget() {
            return target;
        }

        void setTarget(final YangInstanceIdentifier target) {
            this.target = Preconditions.checkNotNull(target);
        }

        SchemaNode getTargetSchemaNode() {
            return targetSchemaNode;
        }

        void setTargetSchemaNode(final SchemaNode targetSchemaNode) {
            this.targetSchemaNode = Preconditions.checkNotNull(targetSchemaNode);
        }

        NormalizedNode<?, ?> getData() {
            return data;
        }

        void setData(final NormalizedNode<?, ?> data) {
            this.data = Preconditions.checkNotNull(data);
        }

        void clear() {
            id = null;
            operation = null;
            target = null;
            targetSchemaNode = null;
            data = null;
        }
    }
}
