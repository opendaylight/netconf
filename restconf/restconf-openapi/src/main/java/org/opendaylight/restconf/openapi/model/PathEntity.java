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
    private final @Nullable PostEntity post;
    private final @Nullable PatchEntity patch;
    private final @Nullable GetEntity get;
    private final @Nullable PutEntity put;
    private final @Nullable DeleteEntity delete;

    public PathEntity(final String path, final PostEntity post, final PatchEntity patch,
            final PutEntity put, final GetEntity get, final DeleteEntity delete) {
        this.path = Objects.requireNonNull(path);
        this.post = post;
        this.patch = patch;
        this.put = put;
        this.delete = delete;
        this.get = get;
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
        final var deleteOperation = delete();
        if (deleteOperation != null) {
            deleteOperation.generate(generator);
        }
        final var getOperation = get();
        if (getOperation != null) {
            getOperation.generate(generator);
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
        return put;
    }

    @Nullable OperationEntity patch() {
        return patch;
    }

    @Nullable OperationEntity get() {
        return get;
    }

    @Nullable OperationEntity delete() {
        return delete;
    }
}
