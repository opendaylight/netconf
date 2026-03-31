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
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;

public final class SecurityEntity extends OpenApiEntity {
    private final @NonNull List<Map<String, List<String>>> security;

    public SecurityEntity(final @NonNull List<Map<String, List<String>>> security) {
        this.security = requireNonNull(security);
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        if (!security.isEmpty()) {
            generator.writeFieldName("security");
            generator.writeStartArray();
            for (final var securityMap : security) {
                generator.writeStartObject();
                for (final var entry : securityMap.entrySet()) {
                    generator.writeArrayFieldStart(entry.getKey());
                    for (final var value : entry.getValue()) {
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
