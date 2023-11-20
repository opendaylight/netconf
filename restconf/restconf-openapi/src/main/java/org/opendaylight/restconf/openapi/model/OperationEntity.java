/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

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

    private final SchemaNode schema;
    private final String deviceName;
    private final String moduleName;
    private final String refPath;
    private final List<ParameterEntity> parameters;
    private final String nodeName;

    protected SchemaNode schema() {
        return schema;
    }

    protected String deviceName() {
        return deviceName;
    }

    protected String moduleName() {
        return moduleName;
    }

    protected List<ParameterEntity> parameters() {
        return parameters;
    }

    protected String refPath() {
        return refPath;
    }

    protected String nodeName() {
        return nodeName;
    }

    public OperationEntity(final SchemaNode schema, final String deviceName, final String moduleName,
            final List<ParameterEntity> parameters, final String refPath) {
        this.schema = schema;
        this.deviceName = deviceName;
        this.moduleName = moduleName;
        this.parameters = parameters;
        this.refPath = refPath;
        this.nodeName = schema.getQName().getLocalName();
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
        final var description = description();
        if (description != null) {
            generator.writeStringField(DESCRIPTION, description);
        }
        final var summary = summary();
        if (summary != null) {
            generator.writeStringField(SUMMARY, summary);
        }
    }

    protected abstract String operation();

    @Nullable Boolean deprecated() {
        return Boolean.FALSE;
    }

    @Nullable String description() {
        return schema().getDescription().orElse("");
    }

    @Nullable abstract String summary();

    void generateRequestBody(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    void generateResponses(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    void generateTags(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeArrayFieldStart("tags");
        generator.writeString(deviceName + " " + moduleName);
        generator.writeEndArray();
    }

    void generateParams(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeArrayFieldStart(PARAMETERS);
        if (!parameters().isEmpty()) {
            for (final var parameter : parameters()) {
                generator.writeStartObject();
                generator.writeStringField(NAME, parameter.name());
                generator.writeStringField(IN, parameter.in());
                generator.writeBooleanField(REQUIRED, parameter.required());
                generator.writeObjectFieldStart(SCHEMA);
                generator.writeStringField(TYPE, parameter.schema().type());
                generator.writeEndObject(); //end of schema
                if (parameter.description() != null) {
                    generator.writeStringField(DESCRIPTION, parameter.description());
                }
                generator.writeEndObject(); //end of parameter
            }
        }
        generator.writeEndArray(); //end of params
    }


    protected static void generateMediaTypeSchemaRef(final JsonGenerator generator, final String mediaType,
            final String ref) throws IOException {
        generator.writeObjectFieldStart(mediaType);
        generator.writeObjectFieldStart(SCHEMA);
        generator.writeStringField(REF, ref);
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
