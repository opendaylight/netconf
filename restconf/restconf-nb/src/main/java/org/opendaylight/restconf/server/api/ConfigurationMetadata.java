/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.time.Instant;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Metadata maintained by a {@link RestconfServer} for configuration resources.
 */
public interface ConfigurationMetadata {
    /**
     * The value of {@code ETag} HTTP header, as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.5.2">RFC8040 Entity-Tag</a>.
     *
     * @param value the value, must not be {@link String#isBlank() blank}
     * @param weak {@code true} if this tag is weak, {@code false} if this tag is strong
     */
    @NonNullByDefault
    record EntityTag(String value, boolean weak) {
        public EntityTag {
            if (value.isBlank()) {
                throw new IllegalArgumentException("Value must not be blank");
            }
        }
    }

    /**
     * The {@code ETag} HTTP header, if supported by the server.
     *
     * @return An {@link EntityTag} or {@code null} if not supported.
     */
    @Nullable EntityTag entityTag();

    /**
     * The {@code Last-Modified} HTTP header, if maintained by the server.
     *
     * @return An {@link Instant} or {@code null} if not maintained.
     */
    @Nullable Instant lastModified();
}
