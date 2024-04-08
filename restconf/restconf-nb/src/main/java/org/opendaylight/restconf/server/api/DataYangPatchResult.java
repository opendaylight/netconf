/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Result of a {@code PATCH} request as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
 *
 * @param status A {@link PatchStatusContext}
 * @param entityTag response {@code ETag} header, or {@code null} if not applicable
 * @param lastModified response {@code Last-Modified} header, or {@code null} if not applicable
 */
public record DataYangPatchResult(
        @NonNull PatchStatusContext status,
        @Nullable EntityTag entityTag,
        @Nullable Instant lastModified) implements ConfigurationMetadata {
    public DataYangPatchResult {
        requireNonNull(status);
    }

    public DataYangPatchResult(final @NonNull PatchStatusContext status) {
        this(status, null, null);
    }
}
