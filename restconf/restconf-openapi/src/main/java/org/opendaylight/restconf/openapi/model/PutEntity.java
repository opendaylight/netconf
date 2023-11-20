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

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class PutEntity extends OperationEntity {

    private final String fullName;

    public PutEntity(final SchemaNode schema, final String deviceName, final String moduleName,
            final List<ParameterEntity> parameters, final String refPath, final String fullName) {
        super(schema, deviceName, moduleName, parameters, refPath);
        this.fullName = fullName;
    }

    @Override
    protected String operation() {
        return "put";
    }

    @Override
    @Nullable String summary() {
        final var nodeName = schema().getQName().getLocalName();
        return SUMMARY_TEMPLATE.formatted(HttpMethod.PUT, moduleName(), deviceName(), nodeName);
    }

    @Override
    void generateResponses(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("responses");
        generator.writeObjectFieldStart(String.valueOf(CREATED.getStatusCode()));
        generator.writeStringField("description", "Created");
        generator.writeEndObject();
        generator.writeObjectFieldStart(String.valueOf(NO_CONTENT.getStatusCode()));
        generator.writeStringField("description", "Updated");
        generator.writeEndObject();
        generator.writeEndObject();
    }

    @Override
    void generateRequestBody(final @NonNull JsonGenerator generator) throws IOException {
        final var nodeName = schema().getQName().getLocalName();
        generator.writeObjectFieldStart("requestBody");
        generator.writeStringField("description", nodeName);
        generator.writeObjectFieldStart("content");
        final var ref = COMPONENTS_PREFIX + moduleName() + "_" + refPath();
        generator.writeObjectFieldStart(MediaType.APPLICATION_JSON);
        generator.writeObjectFieldStart("schema");

        generator.writeObjectFieldStart("properties");
        generator.writeObjectFieldStart(fullName);
        if (schema() instanceof ListSchemaNode) {
            generator.writeStringField("type", "array");
            generator.writeObjectFieldStart("items");
            generator.writeStringField("$ref", ref);
            generator.writeStringField("type", OBJECT);
            generator.writeEndObject();
        } else {
            generator.writeStringField("$ref", ref);
            generator.writeStringField("type", OBJECT);
        }
        generator.writeEndObject();
        generator.writeEndObject();
        generator.writeEndObject();
        generator.writeEndObject();

        generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_XML, ref);
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
