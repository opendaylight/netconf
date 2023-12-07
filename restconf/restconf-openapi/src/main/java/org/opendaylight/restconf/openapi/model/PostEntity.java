/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.opendaylight.restconf.openapi.impl.DefinitionGenerator.OUTPUT_SUFFIX;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DocumentedNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class PostEntity extends OperationEntity {

    private final DocumentedNode parentNode;
    private static final String INPUT_SUFFIX = "_input";
    private static final String INPUT_KEY = "input";
    private static final String POST_DESCRIPTION = """
        \n
        Note:
        In example payload, you can see only the first data node child of the resource to be created, following the
        guidelines of RFC 8040, which allows us to create only one resource in POST request.
        """;

    public PostEntity(final SchemaNode schema, final String deviceName, final String moduleName,
            final List<ParameterEntity> parameters, final String refPath, final DocumentedNode parentNode) {
        super(schema, deviceName, moduleName, parameters, refPath);
        this.parentNode = parentNode;
    }

    protected String operation() {
        return "post";
    }

    @Nullable String summary() {
        if (parentNode instanceof Module) {
            return SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName(), moduleName(), moduleName());
        }
        if (parentNode != null) {
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
                // TODO: add proper discriminator from DefinitionNames when schemas re-implementation is done
                final var ref = COMPONENTS_PREFIX + moduleName() + "_" + operationName + OUTPUT_SUFFIX;
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
                // TODO: add proper discriminator from DefinitionNames when schemas re-implementation is done
                final var ref = COMPONENTS_PREFIX + moduleName() + "_" + operationName + INPUT_SUFFIX;
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
            //TODO: Remove if and fix this weird logic of top level nodes
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
        final var summary = summary();
        if (summary != null) {
            generator.writeStringField(SUMMARY, summary);
        }
    }

    @Override
    @Nullable String description() {
        if (parentNode != null) {
            return parentNode.getDescription().orElse("");
        } else {
            return super.description();
        }
    }
}
