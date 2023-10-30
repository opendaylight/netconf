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
import java.util.Map;

/**
 * TODO use this class to simplify SchemasStream.
 */
public final class ComponentsEntity extends OpenApiEntity {
    private final Map<String, SecuritySchemeEntity> securitySchemes;

    public ComponentsEntity(final Map<String, SecuritySchemeEntity> securitySchemes) {
        this.securitySchemes = securitySchemes;
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("components");
        generator.writeObjectFieldStart("security");
        for (final Map.Entry<String, SecuritySchemeEntity> entry : securitySchemes.entrySet()) {
            generator.writeFieldName(entry.getKey());
            entry.getValue().generate(generator);
        }
        // end security
        generator.writeEndObject();
        // end components
        generator.writeEndObject();
    }
}
