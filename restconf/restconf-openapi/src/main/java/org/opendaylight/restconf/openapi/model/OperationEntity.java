/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Archetype for an Operation.
 */
public abstract sealed class OperationEntity extends OpenApiEntity permits DeleteEntity, GetEntity, PatchEntity,
        PostEntity, PutEntity {
    protected static final String SCHEMA = "schema";
    protected static final String SUMMARY_TEMPLATE = "%s - %s - %s - %s";
    protected static final String RESPONSES = "responses";
    protected static final String DESCRIPTION = "description";
    protected static final String OBJECT = "object";
    protected static final String CONTENT = "content";
    protected static final String COMPONENTS_PREFIX = "#/components/schemas/";
    protected static final String PROPERTIES = "properties";
    protected static final String TYPE = "type";
    protected static final String ARRAY = "array";
    protected static final String ITEMS = "items";
    protected static final String REF = "$ref";
    protected static final String PARAMETERS = "parameters";
    protected static final String SUMMARY = "summary";
    protected static final String NAME = "name";
    protected static final String IN = "in";
    protected static final String REQUIRED = "required";
    protected static final String REQUEST_BODY = "requestBody";

    private final @Nullable SchemaNode schema;
    private final @NonNull String deviceName;
    private final @NonNull String moduleName;
    private final @Nullable String refPath;
    private final @Nullable List<ParameterEntity> parameters;

    protected @Nullable SchemaNode schema() {
        return schema;
    }

    protected @NonNull String deviceName() {
        return deviceName;
    }

    protected @NonNull String moduleName() {
        return moduleName;
    }

    protected @Nullable List<ParameterEntity> parameters() {
        return parameters;
    }

    protected @Nullable String refPath() {
        return refPath;
    }

    public OperationEntity(final @Nullable SchemaNode schema, final @NonNull String deviceName,
            final @NonNull String moduleName, final @Nullable List<ParameterEntity> parameters,
            final @Nullable String refPath) {
        this.schema = schema;
        this.deviceName = requireNonNull(deviceName);
        this.moduleName = requireNonNull(moduleName);
        this.parameters = parameters;
        this.refPath = refPath;
    }

    @Override
    public void generate(@NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(operation());
        generateBasics(generator);
        generateRequestBody(generator);
        generateResponses(generator);
        generateTags(generator);
        generateParams(generator);
        generator.writeEndObject();
    }

    public void generateBasics(@NonNull JsonGenerator generator) throws IOException {
        generator.writeStringField(DESCRIPTION, description());
        generator.writeStringField(SUMMARY, summary());
    }

    protected @NonNull abstract String operation();

    @NonNull String description() {
        return schema == null ? "" : schema.getDescription().orElse("");
    }

    @Nullable String nodeName() {
        return schema == null ? null : schema.getQName().getLocalName();
    }

    @NonNull abstract String summary();

    abstract void generateRequestBody(@NonNull JsonGenerator generator) throws IOException;

    abstract void generateResponses(@NonNull JsonGenerator generator) throws IOException;

    void generateTags(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeArrayFieldStart("tags");
        generator.writeString(deviceName + " " + moduleName);
        generator.writeEndArray();
    }

    void generateParams(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeArrayFieldStart(PARAMETERS);
        final var parametersList = requireNonNull(parameters());
        if (!parametersList.isEmpty()) {
            for (final var parameter : parametersList) {
                generator.writeStartObject();
                generator.writeStringField(NAME, parameter.name());
                generator.writeStringField(IN, parameter.in());
                generator.writeBooleanField(REQUIRED, parameter.required());
                generator.writeObjectFieldStart(SCHEMA);
                if (parameter.schema() != null) {
                    generator.writeStringField(TYPE, parameter.schema().type());
                }
                generator.writeEndObject(); //end of schema
                if (parameter.description() != null) {
                    generator.writeStringField(DESCRIPTION, parameter.description());
                }
                generator.writeEndObject(); //end of parameter
            }
        }
        generator.writeEndArray(); //end of params
    }

    protected static void generateMediaTypeSchemaRef(final @NonNull JsonGenerator generator,
            final @NonNull String mediaType, final @NonNull String ref) throws IOException {
        generator.writeObjectFieldStart(mediaType);
        generator.writeObjectFieldStart(SCHEMA);
        generator.writeStringField(REF, ref);
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
