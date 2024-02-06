/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public final class PathEntity extends OpenApiEntity {
    private final @NonNull String path;
    private final @Nullable PostEntity post;
    private final @Nullable PatchEntity patch;
    private final @Nullable GetEntity get;
    private final @Nullable PutEntity put;
    private final @Nullable DeleteEntity delete;

    /**
     * Create a new config path.
     */
    public PathEntity(final @NonNull String path, final @Nullable PostEntity post, final @NonNull PatchEntity patch,
            final @NonNull PutEntity put, final @NonNull GetEntity get, final @NonNull DeleteEntity delete) {
        this.path = requireNonNull(path);
        this.post = post;
        this.patch = requireNonNull(patch);
        this.put = requireNonNull(put);
        this.delete = requireNonNull(delete);
        this.get = requireNonNull(get);
    }

    /**
     * Possibility to create a new non config or datastore path.
     */
    public PathEntity(final @NonNull String path, final @NonNull GetEntity get) {
        this.path = requireNonNull(path);
        this.get = requireNonNull(get);
        this.post = null;
        this.patch = null;
        this.put = null;
        this.delete = null;
    }

    /**
     * Possibility to create a new root post path or path for operation (RPC/action).
     */
    public PathEntity(final @NonNull String path, final @NonNull PostEntity post) {
        this.path = requireNonNull(path);
        this.post = requireNonNull(post);
        this.patch = null;
        this.put = null;
        this.delete = null;
        this.get = null;
    }

    @Override
    public void generate(@NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(path);
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
