/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(value = Include.NON_NULL)
public record Schema(@JsonProperty("enum") ArrayNode schemaEnum, ArrayNode required, ObjectNode discriminator,
        ObjectNode examples, ObjectNode externalDocs, ObjectNode properties, ObjectNode xml, String description,
        @JsonProperty("$ref") String ref, String title, String type) {

    private Schema(final Builder builder) {
        this(builder.schemaEnum, builder.required, builder.discriminator, builder.examples, builder.externalDocs,
            builder.properties, builder.xml, builder.description, builder.ref, builder.title, builder.type);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private ArrayNode schemaEnum;
        private ArrayNode required;
        private ObjectNode discriminator;
        private ObjectNode examples;
        private ObjectNode externalDocs;
        private ObjectNode properties;
        private ObjectNode xml;
        private String description;
        private String ref;
        private String title;
        private String type;

        public Builder schemaEnum(final ArrayNode schemaEnum) {
            this.schemaEnum = schemaEnum;
            return this;
        }

        public Builder required(final ArrayNode required) {
            this.required = required;
            return this;
        }

        public Builder discriminator(final ObjectNode discriminator) {
            this.discriminator = discriminator;
            return this;
        }

        public Builder examples(final ObjectNode examples) {
            this.examples = examples;
            return this;
        }

        public Builder externalDocs(final ObjectNode externalDocs) {
            this.externalDocs = externalDocs;
            return this;
        }

        public Builder properties(final ObjectNode properties) {
            this.properties = properties;
            return this;
        }

        public Builder xml(final ObjectNode xml) {
            this.xml = xml;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder ref(final String ref) {
            this.ref = ref;
            return this;
        }

        public Builder title(final String title) {
            this.title = title;
            return this;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Schema build() {
            return new Schema(this);
        }
    }
}
