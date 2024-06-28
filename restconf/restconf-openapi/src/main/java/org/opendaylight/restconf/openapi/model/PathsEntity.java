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
import java.util.Deque;
import org.eclipse.jdt.annotation.NonNull;

public final class PathsEntity extends OpenApiEntity {
    private final @NonNull Deque<PathEntity> paths;

    public PathsEntity(final @NonNull Deque<PathEntity> paths) {
        this.paths = requireNonNull(paths);
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("paths");
        for (final var path : paths) {
            path.generate(generator);
        }
        generator.writeEndObject();
    }
}
