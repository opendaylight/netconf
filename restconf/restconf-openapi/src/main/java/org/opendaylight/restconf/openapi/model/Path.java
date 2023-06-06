/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(Include.NON_NULL)
public record Path(@JsonProperty("$ref") String ref, String summary, String description, Operation get,
        Operation put, Operation post, Operation delete, Operation options, Operation head, Operation patch,
        Operation trace, ObjectNode servers) {

    private Path(final Builder builder) {
        this(builder.ref, builder.summary, builder.description, builder.get, builder.put, builder.post,
            builder.delete, builder.options, builder.head, builder.patch, builder.trace, builder.servers);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String ref;
        private String summary;
        private String description;
        private Operation get;
        private Operation put;
        private Operation post;
        private Operation delete;
        private Operation options;
        private Operation head;
        private Operation patch;
        private Operation trace;
        private ObjectNode servers;

        public Builder ref(final String ref) {
            this.ref = ref;
            return this;
        }

        public Builder summary(final String summary) {
            this.summary = summary;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder get(final Operation get) {
            this.get = get;
            return this;
        }

        public Builder put(final Operation put) {
            this.put = put;
            return this;
        }

        public Builder post(final Operation post) {
            this.post = post;
            return this;
        }

        public Builder delete(final Operation delete) {
            this.delete = delete;
            return this;
        }

        public Builder options(final Operation options) {
            this.options = options;
            return this;
        }

        public Builder head(final Operation head) {
            this.head = head;
            return this;
        }

        public Builder patch(final Operation patch) {
            this.patch = patch;
            return this;
        }

        public Builder trace(final Operation trace) {
            this.trace = trace;
            return this;
        }

        public Builder servers(final ObjectNode servers) {
            this.servers = servers;
            return this;
        }

        public Path build() {
            return new Path(this);
        }
    }
}