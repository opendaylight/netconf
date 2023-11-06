/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.opendaylight.restconf.openapi.impl.DefinitionGenerator.OUTPUT_SUFFIX;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

public final class PostEntity extends OperationEntity {
    private static final String SUMMARY_TEMPLATE = "%s - %s - %s - %s";
    private static final String INPUT_SUFFIX = "_input";
    private static final String INPUT_KEY = "input";
    private static final String OBJECT = "object";
    private static final String SCHEMA = "schema";
    private static final String TYPE = "type";
    private static final String DESCRIPTION = "description";
    private static final String COMPONENTS_PREFIX = "#/components/schemas/";

    public PostEntity(final OperationDefinition schema, final String deviceName, final String moduleName) {
        super(schema, deviceName, moduleName);
    }

    protected String operation() {
        return "post";
    }

    @Nullable String summary() {
        final var operationName = schema().getQName().getLocalName() + INPUT_SUFFIX;
        return SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName(), moduleName(), operationName);
    }

    @Override
    void generateResponses(final @NonNull JsonGenerator generator) throws IOException {
        if (schema() instanceof RpcDefinition rpc) {
            generator.writeObjectFieldStart("responses");
            final var output = rpc.getOutput();
            final var operationName = rpc.getQName().getLocalName();
            if (!output.getChildNodes().isEmpty()) {
                // TODO: add proper discriminator from DefinitionNames when schemas re-implementation is done
                final var ref = COMPONENTS_PREFIX + moduleName() + "_" + operationName + OUTPUT_SUFFIX;
                generator.writeObjectFieldStart(String.valueOf(OK.getStatusCode()));
                generator.writeStringField(DESCRIPTION, String.format("RPC %s success", operationName));

                generator.writeObjectFieldStart("content");
                generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_JSON, ref);
                generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_XML, ref);
                generator.writeEndObject();

                generator.writeEndObject();

            } else {
                generator.writeObjectFieldStart(String.valueOf(NO_CONTENT.getStatusCode()));
                generator.writeStringField(DESCRIPTION, String.format("RPC %s success", operationName));
                generator.writeEndObject();

            }
            generator.writeEndObject();
        }
    }

    @Override
    void generateRequestBody(final @NonNull JsonGenerator generator) throws IOException {
        if (schema() instanceof RpcDefinition rpc) {
            generator.writeObjectFieldStart("requestBody");
            final var input = rpc.getInput();
            final var operationName = rpc.getQName().getLocalName();
            generator.writeStringField(DESCRIPTION, operationName + INPUT_SUFFIX);
            generator.writeObjectFieldStart("content");
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

                generator.writeObjectFieldStart("properties");
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
                generator.writeStringField("name", INPUT_KEY);
                generator.writeStringField("namespace", input.getQName().getNamespace().toString());
                generator.writeEndObject();

                generator.writeStringField(TYPE, OBJECT);
                generator.writeEndObject();
                generator.writeEndObject();
            }
            generator.writeEndObject();
            generator.writeEndObject();
        }
    }

    private static void generateMediaTypeSchemaRef(final JsonGenerator generator, final String mediaType,
            final String ref) throws IOException {
        generator.writeObjectFieldStart(mediaType);
        generator.writeObjectFieldStart(SCHEMA);
        generator.writeStringField("$ref", ref);
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
