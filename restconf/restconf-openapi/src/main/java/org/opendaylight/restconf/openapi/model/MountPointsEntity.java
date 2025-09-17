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
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public final class MountPointsEntity extends OpenApiEntity {
    private static final Comparator<Entry<Long, ?>> COMPARATOR = Comparator.comparing(Entry::getKey);

    private final Map<Long, String> mountPoints;

    public MountPointsEntity(final Map<Long, String> mountPoints) {
        this.mountPoints = requireNonNull(mountPoints);
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeStartArray();

        try {
            mountPoints.entrySet().stream().sorted(COMPARATOR).forEachOrdered(entry -> {
                try {
                    generator.writeStartObject();
                    generator.writeNumberField("id", entry.getKey());
                    generator.writeStringField("instance", entry.getValue());
                    generator.writeEndObject();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw unmask(e);
        }

        generator.writeEndArray();
    }

    // Split out to fool CheckStyle to not see we are discarding the unchecked exception
    private static IOException unmask(final UncheckedIOException ex) {
        return ex.getCause();
    }
}
