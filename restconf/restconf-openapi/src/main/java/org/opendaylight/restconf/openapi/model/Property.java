/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.eclipse.jdt.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Property(
        @Nullable String description,
        @Nullable String type,
        @Nullable Property items,
        @Nullable Object example,
        @JsonProperty("default") @Nullable Object defaultValue,
        @Nullable String format,
        @Nullable Xml xml,
        @Nullable String title,
        @JsonProperty("$ref") @Nullable String ref,
        @Nullable Integer minItems,
        @Nullable Integer maxItems,
        boolean uniqueItems,
        @JsonProperty("enum") List<String> enums,
        @Nullable Integer minLength,
        @Nullable Integer maxLength) {

    private Property(final Builder builder) {
        this(builder.description, builder.type, builder.items, builder.example, builder.defaultValue, builder.format,
            builder.xml, builder.title, builder.ref, builder.minItems, builder.maxItems, builder.uniqueItems,
            builder.enums, builder.minLength, builder.maxLength);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String description;
        private String type;
        private Property items;
        private Object example;
        private Object defaultValue;
        private String format;
        private Xml xml;
        private String title;
        private String ref;
        private Integer minItems;
        private Integer maxItems;
        private boolean uniqueItems;
        private List<String> enums;
        private Integer minLength;
        private Integer maxLength;

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Builder items(final Property items) {
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

        public Builder xml(final Xml xml) {
            this.xml = xml;
            return this;
        }

        public Builder title(final String title) {
            this.title = title;
            return this;
        }

        public Builder ref(final String ref) {
            this.ref = ref;
            return this;
        }

        public Builder minItems(final Integer minItems) {
            this.minItems = minItems;
            return this;
        }

        public Builder maxItems(final Integer maxItems) {
            this.maxItems = maxItems;
            return this;
        }

        public Builder uniqueItems(final boolean uniqueItems) {
            this.uniqueItems = uniqueItems;
            return this;
        }

        public Builder enums(final List<String> enums) {
            this.enums = enums;
            return this;
        }

        public Builder minLength(final Integer minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder maxLength(final Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Property build() {
            return new Property(this);
        }
    }
}
