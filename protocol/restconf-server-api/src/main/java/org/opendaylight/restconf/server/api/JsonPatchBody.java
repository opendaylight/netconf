/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2023 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

public final class JsonPatchBody extends PatchBody {
    public JsonPatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    PatchContext toPatchContext(final ResourceContext resource, final InputStream inputStream)
            throws IOException, RequestException {
        try (var jsonReader = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            final var patchId = new AtomicReference<String>();
            final var resultList = requireNonNullValue(read(jsonReader, resource, patchId), EDIT);
            final var id = requireNonNullValue(patchId.get(), PATCH_ID);
            // Note: patchId side-effect of above
            return new PatchContext(id, resultList);
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
                case "edit-id" -> edit.setId(in.nextString());
                case "operation" -> edit.setOperation(Operation.ofName(in.nextString()));
                case "target" -> {
                    // target can be specified completely in request URI
                    final var target = parsePatchTarget(resource, in.nextString());
                    edit.setTarget(target.instance());
                    final var stack = target.inference().toSchemaInferenceStack();
                    if (!stack.isEmpty()) {
                        stack.exit();
                    }

                    if (!stack.isEmpty()) {
                        final var parentStmt = stack.currentStatement();
                        verify(parentStmt instanceof SchemaNode, "Unexpected parent %s", parentStmt);
                    }
                    edit.setTargetSchemaNode(stack.toInference());
                }
                case "value" -> {
                    if (edit.getData() != null || deferredValue != null) {
                        throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE,
                            "Multiple value entries found");
                    }

                    if (edit.getTargetSchemaNode() == null) {
                        // save data defined in value node for next (later) processing, because target needs to be read
                        // always first and there is no ordering in Json input
                        deferredValue = readValueNode(in);
                    } else {
                        // We have a target schema node, reuse this reader without buffering the value.
                        edit.setData(readEditData(in, edit.getTargetSchemaNode(), codecs));
                    }
                }
                default -> {
                    // FIXME: this does not look right, as it can wreck our logic
                }
            }
        }

        in.endObject();

        if (deferredValue != null) {
            // read saved data to normalized node when target schema is already known
            edit.setData(readEditData(new JsonReader(new StringReader(deferredValue)),
                requireNonNullValue(edit.getTargetSchemaNode(), TARGET), codecs));
        }
    }

    /**
     * Parse data defined in value node and saves it to buffer.
     * @param sb Buffer to read value node
     * @param in JsonReader reader
     * @throws IOException if operation fails
     */
    private static String readValueNode(final @NonNull JsonReader in) throws IOException {
        in.beginObject();
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
     */
    private static NormalizedNode readEditData(final @NonNull JsonReader in, final @NonNull Inference targetSchemaNode,
            final @NonNull JSONCodecFactory codecs) {
        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        JsonParserStream.create(writer, codecs, targetSchemaNode).parse(in);
        return resultHolder.getResult().data();
    }

    /**
     * Prepare PatchEntity from PatchEdit instance when it satisfies conditions, otherwise throws exception.
     * @param edit Instance of PatchEdit
     * @return PatchEntity Patch entity
     * @throws RequestException if the {@link PatchEdit} is not consistent
     */
    private static PatchEntity prepareEditOperation(final @NonNull PatchEdit edit) throws RequestException {
        final var operation = requireNonNullValue(edit.getOperation(), OPERATION);
        final var target = requireNonNullValue(edit.getTarget(), TARGET);
        if (edit.getTargetSchemaNode() != null && checkDataPresence(operation, edit.getData() != null)) {
            final var editId = requireNonNullValue(edit.getId(), EDIT_ID);
            if (!requiresValue(operation)) {
                return new PatchEntity(editId, operation, target);
            }

            // for lists allow to manipulate with list items through their parent
            final YangInstanceIdentifier targetNode;
            if (target.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                targetNode = target.getParent();
            } else {
                targetNode = target;
            }

            return new PatchEntity(editId, operation, targetNode, edit.getData());
        }
        throw new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, operation + " operation "
            + (requiresValue(operation) ? "requires '" : "can not have '") + VALUE + "' element");
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