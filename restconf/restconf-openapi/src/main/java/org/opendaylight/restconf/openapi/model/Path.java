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
public record Path(@JsonProperty("$ref") String ref, String summary, String description, ObjectNode get,
    ObjectNode put, ObjectNode post, ObjectNode delete, ObjectNode options, ObjectNode head, ObjectNode patch,
    ObjectNode trace, ObjectNode servers) {

    private Path(final Builder builder) {
        this(builder.ref, builder.summary, builder.description, builder.get, builder.put, builder.post,
                builder.delete, builder.options, builder.head, builder.patch, builder.trace, builder.servers);
    }

    public static class Builder {
        private String ref;
        private String summary;
        private String description;
        private ObjectNode get;
        private ObjectNode put;
        private ObjectNode post;
        private ObjectNode delete;
        private ObjectNode options;
        private ObjectNode head;
        private ObjectNode patch;
        private ObjectNode trace;
        private ObjectNode servers;

        public Builder setRef(final String ref) {
            this.ref = ref;
            return this;
        }

        public Builder setSummary(final String summary) {
            this.summary = summary;
            return this;
        }

        public Builder setDescription(final String description) {
            this.description = description;
            return this;
        }

        public Builder setGet(final ObjectNode get) {
            this.get = get;
            return this;
        }

        public Builder setPut(final ObjectNode put) {
            this.put = put;
            return this;
        }

        public Builder setPost(final ObjectNode post) {
            this.post = post;
            return this;
        }

        public Builder setDelete(final ObjectNode delete) {
            this.delete = delete;
            return this;
        }

        public Builder setOptions(final ObjectNode options) {
            this.options = options;
            return this;
        }

        public Builder setHead(final ObjectNode head) {
            this.head = head;
            return this;
        }

        public Builder setPatch(final ObjectNode patch) {
            this.patch = patch;
            return this;
        }

        public Builder setTrace(final ObjectNode trace) {
            this.trace = trace;
            return this;
        }

        public Builder setServers(final ObjectNode servers) {
            this.servers = servers;
            return this;
        }

        public Path build() {
            return new Path(this);
        }
    }
}