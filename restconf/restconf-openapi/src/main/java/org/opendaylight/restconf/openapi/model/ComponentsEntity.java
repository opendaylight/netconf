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
import org.opendaylight.restconf.openapi.model.security.SecuritySchemeObject;

public final class ComponentsEntity extends OpenApiEntity {
    private final Map<String, Schema> schemas;
    private final Map<String, SecuritySchemeObject> securitySchemes;

    public ComponentsEntity(final Map<String, Schema> schemas,
        final Map<String, SecuritySchemeObject> securitySchemes) {
        this.schemas = schemas;
        this.securitySchemes = securitySchemes;
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeObjectFieldStart("components");
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
