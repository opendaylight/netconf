/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Operation(boolean deprecated, ArrayNode tags, List<Parameter> parameters, ArrayNode security,
        ArrayNode servers, ObjectNode callbacks, ObjectNode externalDocs, ObjectNode requestBody,
        ObjectNode responses, String description, String operationId, String summary) {

    private Operation(final Builder builder) {
        this(builder.deprecated, builder.tags, builder.parameters, builder.security, builder.servers, builder.callbacks,
            builder.externalDocs, builder.requestBody, builder.responses, builder.description, builder.operationId,
            builder.summary);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private boolean deprecated;
        private ArrayNode tags;
        private List<Parameter> parameters;
        private ArrayNode security;
        private ArrayNode servers;
        private ObjectNode callbacks;
        private ObjectNode externalDocs;
        private ObjectNode requestBody;
        private ObjectNode responses;
        private String description;
        private String operationId;
        private String summary;

        public Builder deprecated(final boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        public Builder tags(final ArrayNode tags) {
            this.tags = tags;
            return this;
        }

        public Builder parameters(final List<Parameter> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder security(final ArrayNode security) {
            this.security = security;
            return this;
        }

        public Builder servers(final ArrayNode servers) {
            this.servers = servers;
            return this;
        }

        public Builder callbacks(final ObjectNode callbacks) {
            this.callbacks = callbacks;
            return this;
        }

        public Builder externalDocs(final ObjectNode externalDocs) {
            this.externalDocs = externalDocs;
            return this;
        }

        public Builder requestBody(final ObjectNode requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public Builder responses(final ObjectNode responses) {
            this.responses = responses;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder operationId(final String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder summary(final String summary) {
            this.summary = summary;
            return this;
        }

        public Operation build() {
            return new Operation(this);
        }
    }
}
