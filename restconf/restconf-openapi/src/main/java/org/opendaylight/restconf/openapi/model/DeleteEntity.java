/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static javax.ws.rs.core.Response.Status.NO_CONTENT;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.HttpMethod;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class DeleteEntity extends OperationEntity {

    public DeleteEntity(final SchemaNode schema, final String deviceName, final String moduleName,
            final List<ParameterEntity> parameters, final String refPath) {
        super(schema, deviceName, moduleName, parameters, refPath);
    }

    @Override
    protected String operation() {
        return "delete";
    }

    @Override
    @Nullable String summary() {
        return SUMMARY_TEMPLATE.formatted(HttpMethod.DELETE, deviceName(), moduleName(), nodeName());
    }

    @Override
    void generateResponses(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(RESPONSES);
        generator.writeObjectFieldStart(String.valueOf(NO_CONTENT.getStatusCode()));
        generator.writeStringField(DESCRIPTION, "Deleted");
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
