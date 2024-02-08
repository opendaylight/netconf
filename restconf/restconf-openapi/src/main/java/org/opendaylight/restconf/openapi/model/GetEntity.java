/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.OK;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public sealed class GetEntity extends OperationEntity permits GetRootEntity {
    private final boolean isConfig;

    public GetEntity(final @Nullable SchemaNode schema, final @NonNull String deviceName,
            final @NonNull String moduleName, final @Nullable List<ParameterEntity> parameters,
            final @Nullable String refPath, final boolean isConfig) {
        super(schema, deviceName, moduleName, parameters, refPath);
        this.isConfig = isConfig;
    }

    @Override
    protected @NonNull String operation() {
        return "get";
    }

    @Override
    @NonNull String summary() {
        return SUMMARY_TEMPLATE.formatted(HttpMethod.GET, deviceName(), moduleName(), nodeName());
    }

    @Override
    void generateRequestBody(@NonNull JsonGenerator generator) throws IOException {
        // no-op
    }

    @Override
    void generateResponses(final @NonNull JsonGenerator generator) throws IOException {
        final var ref = COMPONENTS_PREFIX + moduleName() + "_" + refPath();
        generator.writeObjectFieldStart(RESPONSES);
        generator.writeObjectFieldStart(String.valueOf(OK.getStatusCode()));
        generator.writeStringField(DESCRIPTION, String.valueOf(OK.getStatusCode()));
        generator.writeObjectFieldStart(CONTENT);
        generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_XML, ref);
        generator.writeObjectFieldStart(MediaType.APPLICATION_JSON);
        generator.writeObjectFieldStart(SCHEMA);
        generator.writeObjectFieldStart(PROPERTIES);
        generator.writeObjectFieldStart(nodeName());
        if (schema() instanceof ListSchemaNode) {
            generator.writeStringField(TYPE, ARRAY);
            generator.writeObjectFieldStart(ITEMS);
            generator.writeStringField(REF, ref);
            generator.writeStringField(TYPE, OBJECT);
            generator.writeEndObject(); //end of items
        } else {
            generator.writeStringField(REF, ref);
            generator.writeStringField(TYPE, OBJECT);
        }
        generator.writeEndObject(); //end of nodeName
        generator.writeEndObject(); //end of props
        generator.writeEndObject(); //end of schema
        generator.writeEndObject(); //end of json
        generator.writeEndObject(); //end of content
        generator.writeEndObject(); //end of 200
        generator.writeEndObject(); //end of responses
    }

    @Override
    void generateParams(@NonNull JsonGenerator generator) throws IOException {
        final var contentParam = new ParameterEntity(CONTENT, "query", !isConfig,
            new ParameterSchemaEntity("string", List.of("config", "nonconfig", "all")), null);
        final var parameters = requireNonNull(parameters());
        parameters.add(contentParam);
        generator.writeArrayFieldStart(PARAMETERS);
        for (final var parameter : parameters) {
            final var parameterSchema = requireNonNull(parameter.schema());
            generator.writeStartObject();
            generator.writeStringField(NAME, parameter.name());
            generator.writeStringField(IN, parameter.in());
            generator.writeBooleanField(REQUIRED, parameter.required());
            generator.writeObjectFieldStart(SCHEMA);
            final var schemaEnum = parameter.schema().schemaEnum();
            if (schemaEnum != null) {
                generator.writeArrayFieldStart("enum");
                for (final var enumCase : schemaEnum) {
                    generator.writeString(enumCase);
                }
                generator.writeEndArray(); //end of enum
            }
            generator.writeStringField(TYPE, parameterSchema.type());
            generator.writeEndObject(); //end of schema
            if (parameter.description() != null) {
                generator.writeStringField(DESCRIPTION, parameter.description());
            }
            generator.writeEndObject(); //end of parameter
        }
        generator.writeEndArray(); //end of params
        parameters.remove(contentParam);
    }
}
