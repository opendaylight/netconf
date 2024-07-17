/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.time.Instant;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Result of a {@code PUT} request as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>. The definition makes it
 * clear that the logical operation is {@code create-or-replace}.
 *
 * @param created {@code true} if the resource was created, {@code false} if it was replaced
 * @param entityTag response {@code ETag} header, or {@code null} if not applicable
 * @param lastModified response {@code Last-Modified} header, or {@code null} if not applicable
 */
public record DataPutResult(
        boolean created,
        @Nullable EntityTag entityTag,
        @Nullable Instant lastModified) implements ConfigurationMetadata {
    public DataPutResult(final boolean created) {
        this(created, null, null);
    }
}