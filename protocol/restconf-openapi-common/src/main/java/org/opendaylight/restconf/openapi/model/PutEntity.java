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

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class PutEntity extends OperationEntity {
    private final @NonNull String fullName;

    public PutEntity(final @NonNull SchemaNode schema, final @NonNull String deviceName,
            final @NonNull String moduleName, final @NonNull List<ParameterEntity> parameters,
            final @NonNull String refPath, final @NonNull String fullName) {
        super(schema, deviceName, moduleName, parameters, refPath);
        this.fullName = requireNonNull(fullName);
    }

    @Override
    protected @NonNull String operation() {
        return "put";
    }

    @Override
    @NonNull String summary() {
        return SUMMARY_TEMPLATE.formatted(HttpMethod.PUT, moduleName(), deviceName(), nodeName());
    }

    @Override
    void generateResponses(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(RESPONSES);
        generator.writeObjectFieldStart(String.valueOf(CREATED.getStatusCode()));
        generator.writeStringField(DESCRIPTION, "Created");
        generator.writeEndObject(); //end of 201
        generator.writeObjectFieldStart(String.valueOf(NO_CONTENT.getStatusCode()));
        generator.writeStringField(DESCRIPTION, "Updated");
        generator.writeEndObject(); //end of 204
        generator.writeEndObject();
    }

    @Override
    void generateRequestBody(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(REQUEST_BODY);
        generator.writeStringField(DESCRIPTION, nodeName());
        generator.writeObjectFieldStart(CONTENT);
        final var ref = COMPONENTS_PREFIX + moduleName() + "_" + refPath();
        generator.writeObjectFieldStart(MediaType.APPLICATION_JSON);
        generator.writeObjectFieldStart(SCHEMA);
        generator.writeObjectFieldStart(PROPERTIES);
        generator.writeObjectFieldStart(fullName);
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
        generator.writeEndObject(); //end of moduleName
        generator.writeEndObject(); //end of props
        generator.writeEndObject(); //end of schema
        generator.writeEndObject(); //end of json

        generateMediaTypeSchemaRef(generator, MediaType.APPLICATION_XML, ref);
        generator.writeEndObject(); //end of content
        generator.writeEndObject(); //end of request body
    }
}
