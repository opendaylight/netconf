/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public record Property(
        @NonNull String name,
        @Nullable String description,
        @Nullable String type,
        @Nullable String items,
        @Nullable Object example,
        @JsonProperty("default") @Nullable Object defaultValue,
        @Nullable String format,
        @Nullable Property property) {

    private Property(final Builder builder) {
        this(builder.name, builder.description, builder.type, builder.items, builder.example, builder.defaultValue,
            builder.format, builder.property);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String name;
        private String description;
        private String type;
        private String items;
        private Object example;
        private Object defaultValue;
        private String format;
        private Property property;

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Builder items(final String items) {
            this.items = items;
            return this;
        }

        public Builder example(final Object example) {
            this.example = example;
            return this;
        }

        public Builder defaultValue(final Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder format(final String format) {
            this.format = format;
            return this;
        }

        public Builder property(final Property property) {
            this.property = property;
            return this;
        }

        public Property build() {
            return new Property(this);
        }
    }
}
