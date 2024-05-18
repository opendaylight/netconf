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
import org.opendaylight.restconf.api.FormattableBody;

/**
 * Result of a {@code GET} request as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.3">RFC8040 section 4.3</a>.
 *
 * @param body Resulting body
 * @param entityTag response {@code ETag} header, or {@code null} if not applicable
 * @param lastModified response {@code Last-Modified} header, or {@code null} if not applicable
 */
public record DataGetResult(
        @NonNull FormattableBody body,
        @Nullable EntityTag entityTag,
        @Nullable Instant lastModified) implements ConfigurationMetadata {
    public DataGetResult {
        requireNonNull(body);
    }

    public DataGetResult(final @NonNull FormattableBody body) {
        this(body, null, null);
    }
}
