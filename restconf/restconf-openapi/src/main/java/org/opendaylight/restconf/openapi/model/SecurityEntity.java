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
import java.util.Map;

public final class SecurityEntity extends OpenApiEntity {
    private final List<Map<String, List<String>>> security;

    public SecurityEntity(final List<Map<String, List<String>>> security) {
        this.security = security;
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        if (security != null && !security.isEmpty()) {
            generator.writeFieldName("security");
            generator.writeStartArray();
            for (final Map<String, List<String>> securityMap : security) {
                generator.writeStartObject();
                for (final Map.Entry<String, List<String>> entry : securityMap.entrySet()) {
                    generator.writeArrayFieldStart(entry.getKey());
                    for (final String value : entry.getValue()) {
                        generator.writeString(value);
                    }
                    generator.writeEndArray();
                }
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }
    }
}
