/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.model.security.Http;
import org.opendaylight.restconf.openapi.model.security.SecuritySchemeObject;

public final class SecuritySchemesEntity extends OpenApiEntity {
    private final @NonNull Map<String, SecuritySchemeObject> schemes;

    public SecuritySchemesEntity(final @NonNull Map<String, SecuritySchemeObject> schemes) {
        this.schemes = requireNonNull(schemes);
    }

    @Override
    public void generate(@NonNull final JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("securitySchemes");
        for (final var entry : schemes.entrySet()) {
            generator.writeObjectFieldStart(entry.getKey());
            if (entry.getValue() instanceof Http http) {
                generator.writeStringField("scheme", http.scheme());
                generator.writeStringField("type", http.type().toString());
                if (http.description() != null) {
                    generator.writeStringField("description", http.description());
                }
                if (http.bearerFormat() != null) {
                    generator.writeStringField("bearerFormat", http.bearerFormat());
                }
            }
            generator.writeEndObject();
            generator.writeEndObject();
        }
    }
}
