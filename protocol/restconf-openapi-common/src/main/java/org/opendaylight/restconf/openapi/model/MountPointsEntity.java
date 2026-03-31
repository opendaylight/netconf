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
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public final class MountPointsEntity extends OpenApiEntity {
    private final Map<String, Long> mountPoints;

    public MountPointsEntity(final Map<String, Long> mountPoints) {
        this.mountPoints = requireNonNull(mountPoints);
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeStartArray();
        for (final var entry : mountPoints.entrySet()) {
            generator.writeStartObject();
            generator.writeStringField("instance", entry.getKey());
            generator.writeStringField("id", entry.getValue().toString());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }
}
