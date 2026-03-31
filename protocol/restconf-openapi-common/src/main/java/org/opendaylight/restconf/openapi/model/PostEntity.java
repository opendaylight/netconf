/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DocumentedNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class PostEntity extends OperationEntity {
    private static final String INPUT_SUFFIX = "_input";
    private static final String INPUT_KEY = "input";
    private static final String POST_DESCRIPTION = """
        \n
        Note:
        In example payload, you can see only the first data node child of the resource to be created, following the
        guidelines of RFC 8040, which allows us to create only one resource in POST request.
        """;

    private final String deviceName;
    private final boolean isRootTag;
    private final @Nullable DocumentedNode parentNode;
    private final @NonNull List<SchemaNode> parentNodes;

    public PostEntity(final @NonNull SchemaNode schema, final @NonNull String deviceName,
            final @NonNull String moduleName, final @NonNull List<ParameterEntity> parameters,
            final @NonNull String refPath, final @Nullable DocumentedNode parentNode,
            final @NonNull List<SchemaNode> parentNodes, final boolean isRootTag) {
        super(requireNonNull(schema), deviceName, moduleName, requireNonNull(parameters), requireNonNull(refPath));
        this.parentNode = parentNode;
        this.parentNodes = requireNonNull(parentNodes);
        this.isRootTag = isRootTag;
        this.deviceName = deviceName;
    }

    protected @NonNull String operation() {
        return "post";
    }

    @NonNull String summary() {
        if (parentNode instanceof Module) {
            return SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName(), moduleName(), moduleName());
        }
        if (parentNode != null && !(schema() instanceof OperationDefinition)) {
            return SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName(), moduleName(),
                ((DataSchemaNode) parentNode).getQName().getLocalName());
        }
        return SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName(), moduleName(), nodeName());
    }

    @Override
    void generateResponses(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(RESPONSES);
        if (schema() instanceof OperationDefinition rpc) {
            final var output = rpc.getOutput();
            final var operationName = rpc.getQName().getLocalName();
            if (!output.getChildNodes().isEmpty()) {
                final var ref = processOperationsRef(rpc, operationName, "_output");
                generator.writeObjectFieldStart(String.valueOf(OK.getStatusCode()));
                generator.writeStringField(DESCRIPTION, String.format("RPC %s success", operationName));

                generator.writeObjectFieldStart(CONTENT);
                generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_JSON, ref);
                generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_XML, ref);
                generator.writeEndObject();

                generator.writeEndObject();

            } else {
                generator.writeObjectFieldStart(String.valueOf(NO_CONTENT.getStatusCode()));
                generator.writeStringField(DESCRIPTION, String.format("RPC %s success", operationName));
                generator.writeEndObject();

            }
        } else {
            generator.writeObjectFieldStart(String.valueOf(CREATED.getStatusCode()));
            generator.writeStringField(DESCRIPTION, "Created");
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    @Override
    void generateRequestBody(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(REQUEST_BODY);
        if (schema() instanceof OperationDefinition rpc) {
            final var input = rpc.getInput();
            final var operationName = rpc.getQName().getLocalName();
            generator.writeStringField(DESCRIPTION, operationName + INPUT_SUFFIX);
            generator.writeObjectFieldStart(CONTENT);
            if (!input.getChildNodes().isEmpty()) {
                final var ref = processOperationsRef(rpc, operationName, INPUT_SUFFIX);
                generator.writeObjectFieldStart(MediaType.APPLICATION_JSON);
                generator.writeObjectFieldStart(SCHEMA);
                generator.writeObjectFieldStart("properties");
                generator.writeObjectFieldStart(INPUT_KEY);
                generator.writeStringField("$ref", ref);
                generator.writeStringField(TYPE, OBJECT);
                generator.writeEndObject();
                generator.writeEndObject();
                generator.writeEndObject();
                generator.writeEndObject();
                generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_XML, ref);
            } else {
                generator.writeObjectFieldStart(MediaType.APPLICATION_JSON);
                generator.writeObjectFieldStart(SCHEMA);

                generator.writeObjectFieldStart(PROPERTIES);
                generator.writeObjectFieldStart(INPUT_KEY);
                generator.writeStringField(TYPE, OBJECT);
                generator.writeEndObject();
                generator.writeEndObject();

                generator.writeStringField(TYPE, OBJECT);
                generator.writeEndObject();
                generator.writeEndObject();

                generator.writeObjectFieldStart(MediaType.APPLICATION_XML);
                generator.writeObjectFieldStart(SCHEMA);

                generator.writeObjectFieldStart("xml");
                generator.writeStringField(NAME, INPUT_KEY);
                generator.writeStringField("namespace", input.getQName().getNamespace().toString());
                generator.writeEndObject();

                generator.writeStringField(TYPE, OBJECT);
                generator.writeEndObject();
                generator.writeEndObject();
            }
            generator.writeEndObject();
        } else {
            generator.writeStringField(DESCRIPTION, nodeName());
            generator.writeObjectFieldStart(CONTENT);
            final var ref = COMPONENTS_PREFIX + moduleName() + "_" + refPath();
            var childConfig = true;
            if (schema() instanceof DataNodeContainer dataSchema) {
                final var child = dataSchema.getChildNodes().stream()
                    .filter(n -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
                    .findFirst().orElse(null);
                if (child != null) {
                    childConfig = child.isConfiguration();
                }
            }
            if (parentNode == null && !childConfig) {
                generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_JSON, ref);
            } else {
                generator.writeObjectFieldStart(MediaType.APPLICATION_JSON);
                generator.writeObjectFieldStart(SCHEMA);

                generator.writeObjectFieldStart(PROPERTIES);
                generator.writeObjectFieldStart(nodeName());
                if (schema() instanceof ListSchemaNode) {
                    generator.writeStringField(TYPE, ARRAY);
                    generator.writeObjectFieldStart(ITEMS);
                    generator.writeStringField(REF, ref);
                    generator.writeStringField(TYPE, OBJECT);
                    generator.writeEndObject();
                } else {
                    generator.writeStringField(REF, ref);
                    generator.writeStringField(TYPE, OBJECT);
                }
                generator.writeEndObject();
                generator.writeEndObject();
                generator.writeEndObject();
                generator.writeEndObject();
            }
            generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_XML, ref);
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    @Override
    public void generateBasics(@NonNull JsonGenerator generator) throws IOException {
        final var description = description();
        if (schema() instanceof OperationDefinition) {
            generator.writeStringField(DESCRIPTION, description);
        } else {
            generator.writeStringField(DESCRIPTION, description + POST_DESCRIPTION);
        }
        generator.writeStringField(SUMMARY, summary());
    }

    @Override
    @NonNull String description() {
        if (parentNode != null && !(schema() instanceof OperationDefinition)) {
            return parentNode.getDescription().orElse("");
        } else {
            return super.description();
        }
    }

    private String processOperationsRef(final OperationDefinition def, final String operationName, final String suf) {
        final var ref = new StringBuilder(COMPONENTS_PREFIX + moduleName() + "_");
        if (def instanceof ActionDefinition) {
            final boolean hasChildNodes = suf.equals(INPUT_SUFFIX) ? !def.getInput().getChildNodes().isEmpty()
                : !def.getOutput().getChildNodes().isEmpty();
            if (hasChildNodes) {
                for (final SchemaNode node : parentNodes) {
                    ref.append(node.getQName().getLocalName()).append("_");
                }
            }
        }
        return ref.append(operationName).append(suf).toString();
    }

    @Override
    void generateTags(final @NonNull JsonGenerator generator) throws IOException {
        if (isRootTag) {
            generator.writeArrayFieldStart("tags");
            generator.writeString(deviceName + " root");
            generator.writeEndArray();
        } else {
            super.generateTags(generator);
        }
    }
}
