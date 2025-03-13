/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2023 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonPatchBody extends PatchBody {
    private static final Logger LOG = LoggerFactory.getLogger(JsonPatchBody.class);

    public JsonPatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    PatchContext toPatchContext(final ResourceContext resource, final InputStream inputStream)
            throws IOException, RequestException {
        try (var jsonReader = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            final var patchId = new AtomicReference<String>();
            final var resultList = read(jsonReader, resource, patchId);
            // Note: patchId side-effect of above
            return PatchContext.create(patchId.get(), resultList);
        }
    }

    private static ImmutableList<PatchEntity> read(final JsonReader in, final @NonNull ResourceContext resource,
            final AtomicReference<String> patchId) throws IOException, RequestException {
        final var edits = ImmutableList.<PatchEntity>builder();
        final var edit = new PatchEdit();

        while (in.hasNext()) {
            switch (in.peek()) {
                case NUMBER, STRING -> in.nextString();
                case BOOLEAN -> Boolean.toString(in.nextBoolean());
                case NULL -> in.nextNull();
                case BEGIN_ARRAY -> in.beginArray();
                case BEGIN_OBJECT -> in.beginObject();
                case NAME -> parseByName(in.nextName(), edit, in, resource, edits, patchId);
                case END_OBJECT -> in.endObject();
                case END_ARRAY -> in.endArray();
                default -> {
                    // No-op, including END_DOCUMENT
                }
            }
        }

        return edits.build();
    }

    // Switch value of parsed JsonToken.NAME and read edit definition or patch id
    private static void parseByName(final @NonNull String name, final @NonNull PatchEdit edit,
            final @NonNull JsonReader in, final @NonNull ResourceContext resource,
            final @NonNull Builder<PatchEntity> resultCollection, final @NonNull AtomicReference<String> patchId)
                throws IOException, RequestException  {
        switch (name) {
            case "edit" -> {
                if (in.peek() == JsonToken.BEGIN_ARRAY) {
                    in.beginArray();

                    while (in.hasNext()) {
                        readEditDefinition(edit, in, resource);
                        resultCollection.add(prepareEditOperation(edit));
                        edit.clear();
                    }

                    in.endArray();
                } else {
                    readEditDefinition(edit, in, resource);
                    resultCollection.add(prepareEditOperation(edit));
                    edit.clear();
                }
            }
            case "patch-id" -> patchId.set(in.nextString());
            default -> {
                // No-op
            }
        }
    }

    // Read one patch edit object from JSON input
    private static void readEditDefinition(final @NonNull PatchEdit edit, final @NonNull JsonReader in,
            final @NonNull ResourceContext resource) throws IOException, RequestException {
        String deferredValue = null;
        in.beginObject();

        final var codecs = resource.path.databind().jsonCodecs();

        while (in.hasNext()) {
            final String editDefinition = in.nextName();
            switch (editDefinition) {
                case "edit-id" -> {
                    verifyStringValueType(in.peek(), editDefinition);
                    edit.setId(in.nextString());
                }
                case "operation" -> {
                    verifyStringValueType(in.peek(), editDefinition);
                    final Operation operation;
                    try {
                        operation = Operation.ofName(in.nextString());
                    } catch (IllegalArgumentException e) {
                        LOG.error("Provided operation type does not match", e);
                        throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE, e);
                    }
                    edit.setOperation(operation);
                }
                case "target" -> {
                    verifyStringValueType(in.peek(), editDefinition);
                    // target can be specified completely in request URI
                    final var target = parsePatchTarget(resource, in.nextString());
                    edit.setTarget(target.instance());
                    final var stack = target.inference().toSchemaInferenceStack();
                    if (!stack.isEmpty()) {
                        stack.exit();
                    }

                    if (!stack.isEmpty()) {
                        final var parentStmt = stack.currentStatement();
                        verify(parentStmt instanceof SchemaNode, "Unexpected parent " + parentStmt);
                    }
                    edit.setTargetSchemaNode(stack.toInference());
                }
                case "value" -> {
                    verify(edit.getData() == null && deferredValue == null, "Multiple value entries found");

                    if (edit.getTargetSchemaNode() == null) {
                        // save data defined in value node for next (later) processing, because target needs to be read
                        // always first and there is no ordering in Json input
                        deferredValue = readValueNode(in);
                    } else {
                        // We have a target schema node, reuse this reader without buffering the value.
                        edit.setData(readEditData(in, edit.getTargetSchemaNode(), codecs));
                    }
                }
                default -> throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE,
                    "Provided unknown field " + QName.create(Edit.QNAME, editDefinition));
            }
        }

        in.endObject();

        if (deferredValue != null) {
            // read saved data to normalized node when target schema is already known
            edit.setData(readEditData(new JsonReader(new StringReader(deferredValue)),
                requireNonNullValue(edit.getTargetSchemaNode(), QName.create(Edit.QNAME, "target")), codecs));
        }
    }

    /**
     * Check if provided {@link JsonToken} is a STRING type. If not throws {@link RequestException}.
     *
     * @param token {@link JsonToken}
     * @param editDefinition Node name from {@link Edit} list
     * @throws RequestException if provided {@link JsonToken} is not equals to {@link JsonToken#STRING}
     */
    private static void verifyStringValueType(final JsonToken token, final String editDefinition)
            throws RequestException {
        if (token != JsonToken.STRING) {
            throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE,
                "Expected STRING for value of '" + editDefinition + "', but received " + token);
        }
    }

    /**
     * Parse data defined in value node and saves it to buffer.
     * @param sb Buffer to read value node
     * @param in JsonReader reader
     * @throws IOException if operation fails
     */
    private static String readValueNode(final @NonNull JsonReader in) throws IOException, RequestException {
        in.beginObject();
        verify(in.peek() == JsonToken.NAME, "Empty 'value' field is not allowed");
        final var sb = new StringBuilder().append("{\"").append(in.nextName()).append("\":");

        switch (in.peek()) {
            case BEGIN_ARRAY -> {
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
            }
            default -> readValueObject(sb, in);
        }

        in.endObject();
        return sb.append('}').toString();
    }

    /**
     * Parse one value object of data and saves it to buffer.
     * @param sb Buffer to read value object
     * @param in JsonReader reader
     * @throws IOException if operation fails
     */
    private static void readValueObject(final @NonNull StringBuilder sb, final @NonNull JsonReader in)
        throws IOException {
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
                case STRING -> sb.append('"').append(in.nextString()).append('"');
                case BEGIN_ARRAY -> {
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
                }
                default -> readValueObject(sb, in);
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
     * @throws RequestException if parsing of {@link JsonReader} fails due to invalid data.
     */
    private static NormalizedNode readEditData(final @NonNull JsonReader in, final @NonNull Inference targetSchemaNode,
            final @NonNull JSONCodecFactory codecs) throws RequestException {
        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        try {
            JsonParserStream.create(writer, codecs, targetSchemaNode).parse(in);
            return resultHolder.getResult().data();
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException e) {
            LOG.error("Failed to parse provided JSON data", e);
            throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    /**
     * Prepare PatchEntity from PatchEdit instance when it satisfies conditions, otherwise throws exception.
     * @param edit Instance of PatchEdit
     * @return PatchEntity Patch entity
     * @throws RequestException if the {@link PatchEdit} is not consistent
     */
    private static PatchEntity prepareEditOperation(final @NonNull PatchEdit edit) throws RequestException {
        final var operation = edit.getOperation();
        requireNonNullValue(operation, QName.create(Edit.QNAME, "operation"));
        requireNonNullValue(edit.getTargetSchemaNode(), QName.create(Edit.QNAME, "target"));
        if (checkDataPresence(operation, edit.getData() != null)) {
            if (!requiresValue(operation)) {
                return new PatchEntity(edit.getId(), operation, edit.getTarget());
            }

            // for lists allow to manipulate with list items through their parent
            final YangInstanceIdentifier targetNode;
            if (edit.getTarget().getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                targetNode = edit.getTarget().getParent();
            } else {
                targetNode = edit.getTarget();
            }

            return new PatchEntity(edit.getId(), operation, targetNode, edit.getData());
        }

        throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE, "Provided 'operation' value "
            + operation + " requires" + (requiresValue(operation) ? " a non-empty" : " an empty")
            + " value for the 'value' field");
    }

    /**
     * Check if data is present when operation requires it and not present when operation data is not allowed.
     * @param operation Name of operation
     * @param hasData Data in edit are present/not present
     * @return true if data is present when operation requires it or if there are no data when operation does not
     *     allow it, false otherwise
     */
    private static boolean checkDataPresence(final @NonNull Operation operation, final boolean hasData) {
        return requiresValue(operation)  == hasData;
    }

    /**
     * If provided parameter condition is false, throw a {@link RequestException}.
     *
     * @param condition {@code boolean}
     * @param message message thrown if condition is false
     * @throws RequestException if condition is false
     */
    private static void verify(final boolean condition, final String message) throws RequestException {
        if (!condition) {
            throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE, message);
        }
    }

    /**
     * Check if provided value is not null.
     *
     * @param value node value
     * @param qname node QName
     * @return provided value if it is not null, otherwise throws RequestException
     * @throws RequestException if the value is null
     */
    static <T> T requireNonNullValue(final T value, final QName qname) throws RequestException {
        if (value == null) {
            throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE,
                "Missing required field " + qname);
        }
        return value;
    }

    /**
     * Helper class representing one patch edit.
     */
    private static final class PatchEdit {
        private String id;
        private Operation operation;
        private YangInstanceIdentifier target;
        private Inference targetSchemaNode;
        private NormalizedNode data;

        String getId() {
            return id;
        }

        void setId(final String id) {
            this.id = requireNonNull(id);
        }

        Operation getOperation() {
            return operation;
        }

        void setOperation(final Operation operation) {
            this.operation = requireNonNull(operation);
        }

        YangInstanceIdentifier getTarget() {
            return target;
        }

        void setTarget(final YangInstanceIdentifier target) {
            this.target = requireNonNull(target);
        }

        Inference getTargetSchemaNode() {
            return targetSchemaNode;
        }

        void setTargetSchemaNode(final Inference targetSchemaNode) {
            this.targetSchemaNode = requireNonNull(targetSchemaNode);
        }

        NormalizedNode getData() {
            return data;
        }

        void setData(final NormalizedNode data) {
            this.data = requireNonNull(data);
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