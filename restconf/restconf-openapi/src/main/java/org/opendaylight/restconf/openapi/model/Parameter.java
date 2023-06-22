/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Parameter(
        @NonNull String name,
        @NonNull String in,
        boolean required,
        @Nullable Schema schema,
        @Nullable String description) {

    public Parameter {
        requireNonNull(name);
        requireNonNull(in);
    }

    private Parameter(final Builder builder) {
        this(builder.name, builder.in, builder.required, builder.schema, builder.description);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String name;
        private String in;
        private String description;
        private boolean required;
        private Schema schema;

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder in(final String in) {
            this.in = in;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder required(final boolean required) {
            this.required = required;
            return this;
        }

        public Builder schema(final Schema schema) {
            this.schema = schema;
            return this;
        }

        public Parameter build() {
            return new Parameter(this);
        }
    }
}
