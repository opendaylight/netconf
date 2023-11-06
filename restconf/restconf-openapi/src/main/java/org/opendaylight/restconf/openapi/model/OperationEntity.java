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
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;

/**
 * Archetype for an Operation.
 */
public abstract sealed class OperationEntity extends OpenApiEntity permits PostEntity {
    private final OperationDefinition schema;
    private final String deviceName;
    private final String moduleName;

    protected OperationDefinition schema() {
        return schema;
    }

    protected String deviceName() {
        return deviceName;
    }

    protected String moduleName() {
        return moduleName;
    }

    public OperationEntity(final OperationDefinition schema, final String deviceName, final String moduleName) {
        this.schema = schema;
        this.deviceName = deviceName;
        this.moduleName = moduleName;
    }

    @Override
    public void generate(@NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(operation());
        final var deprecated = deprecated();
        if (deprecated != null) {
            generator.writeBooleanField("deprecated", deprecated);
        }
        final var description = description();
        if (description != null) {
            generator.writeStringField("description", description);
        }
        final var summary = summary();
        if (summary != null) {
            generator.writeStringField("summary", summary);
        }
        generateRequestBody(generator);
        generateResponses(generator);
        generateTags(generator);
        generateParams(generator);
        generator.writeEndObject();
    }

    protected abstract String operation();

    @Nullable Boolean deprecated() {
        return Boolean.FALSE;
    }

    @Nullable String description() {
        return schema.getDescription().orElse("");
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
        generator.writeArrayFieldStart("parameters");
        // need empty array here
        generator.writeEndArray();
    }
}
