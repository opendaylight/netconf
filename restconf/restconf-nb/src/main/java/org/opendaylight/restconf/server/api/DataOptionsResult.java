/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Result of an {@code OPTIONS} HTTP request on a {@code /data} resource, as specified by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.1">RFC8040 section 4.1</a>.
 */
@NonNullByDefault
public record DataOptionsResult(ImmutableSet<String> methods, ImmutableSet<String> patchMediaTypes) {
    public DataOptionsResult {
        requireNonNull(methods);
        requireNonNull(patchMediaTypes);
        if (!methods.contains("OPTIONS")) {
            throw new IllegalArgumentException("OPTIONS method must be reported");
        }
        if (methods.contains("PATCH") && patchMediaTypes.isEmpty()) {
            throw new IllegalArgumentException("PATCH method reported without any media types");
        }
    }
}
