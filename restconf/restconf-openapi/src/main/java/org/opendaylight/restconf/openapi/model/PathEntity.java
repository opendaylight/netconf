/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public final class PathEntity extends OpenApiEntity {
    private final @NonNull String path;
    private final @Nullable OperationEntity post;

    public PathEntity(final String path, final OperationEntity post) {
        this.path = Objects.requireNonNull(path);
        this.post = post;
    }

    @Override
    public void generate(@NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(path);
        final var ref = ref();
        if (ref != null) {
            generator.writeStringField("$ref", ref);
        }
        final var summary = summary();
        if (summary != null) {
            generator.writeStringField("summary", summary);
        }
        final var description = description();
        if (ref != null) {
            generator.writeStringField("description", description);
        }
        final var postOperation = post();
        if (postOperation != null) {
            postOperation.generate(generator);
        }
        final var putOperation = put();
        if (putOperation != null) {
            putOperation.generate(generator);
        }
        final var patchOperation = patch();
        if (patchOperation != null) {
            patchOperation.generate(generator);
        }
        generator.writeEndObject();
    }

    @Nullable String ref() {
        return null;
    }

    @Nullable String summary() {
        return null;
    }

    @Nullable String description() {
        return null;
    }

    @Nullable OperationEntity post() {
        return post;
    }

    @Nullable OperationEntity put() {
        return null;
    }

    @Nullable OperationEntity patch() {
        return null;
    }
}
