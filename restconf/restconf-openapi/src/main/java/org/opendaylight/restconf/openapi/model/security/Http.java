/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model.security;

import static com.google.common.base.Verify.verifyNotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Http(
    @NonNull String scheme,
    @Nullable String description,
    @Nullable String bearerFormat) implements SecuritySchemeObject {
    private static final Type TYPE = Type.http;

    private Http(final Builder builder) {
        this(builder.scheme, builder.description, builder.bearerFormat);
    }

    @Override
    public Type getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "Http[type=" + TYPE
            + ", scheme=" + scheme
            + ", description=" + description
            + ", bearerFormat=" + bearerFormat + "]";
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String description;
        private String scheme;
        private String bearerFormat;

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder scheme(final String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder bearerFormat(final String bearerFormat) {
            this.bearerFormat = bearerFormat;
            return this;
        }

        public Http build() {
            verifyNotNull(scheme, "The Security Scheme Object's scheme must not be null");
            return new Http(this);
        }
    }
}
