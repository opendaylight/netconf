/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model.security;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public record Http(
        @NonNull String scheme,
        @Nullable String description,
        @Nullable String bearerFormat) implements SecuritySchemeObject {
    private static final Type TYPE = Type.http;

    public Http {
        requireNonNull(scheme);
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "Http[type=" + TYPE
            + ", scheme=" + scheme
            + ", description=" + description
            + ", bearerFormat=" + bearerFormat + "]";
    }
}
