/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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

public final class MetadataEntity extends OpenApiEntity {
    private final @NonNull Map<String, ?> mappedMetadata;

    public MetadataEntity(final @NonNull Map<String, ?> mappedMetadata) {
        this.mappedMetadata = requireNonNull(mappedMetadata);
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeObjectFieldStart("metadata");
        for (final var entry : mappedMetadata.entrySet()) {
            generator.writeStringField(entry.getKey(), entry.getValue().toString());
        }
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
