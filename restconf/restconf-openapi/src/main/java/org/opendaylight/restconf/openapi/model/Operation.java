/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Operation(
        Boolean deprecated,
        List<String> tags,
        Set<Parameter> parameters,
        List<Map<String, List<String>>> security,
        List<Server> servers,
        Map<String, Path> callbacks,
        ExternalDocumentation externalDocs,
        RequestBody requestBody,
        Map<String, ResponseObject> responses,
        String description,
        String operationId,
        String summary) {

    public Operation {
        tags = tags == null ? null : List.copyOf(tags);
        parameters = parameters == null ? null : Set.copyOf(parameters);
        security = security == null ? null : List.copyOf(security);
        servers = servers == null ? null : List.copyOf(servers);
        callbacks = callbacks == null ? null : Map.copyOf(callbacks);
        responses = responses == null ? null : Map.copyOf(responses);
    }

    private Operation(final Builder builder) {
        this(builder.deprecated, builder.tags, builder.parameters, builder.security, builder.servers, builder.callbacks,
            builder.externalDocs, builder.requestBody, builder.responses, builder.description, builder.operationId,
            builder.summary);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private Boolean deprecated;
        private List<String> tags;
        private Set<Parameter> parameters;
        private List<Map<String, List<String>>> security;
        private List<Server> servers;
        private Map<String, Path> callbacks;
        private ExternalDocumentation externalDocs;
        private RequestBody requestBody;
        private Map<String, ResponseObject> responses;
        private String description;
        private String operationId;
        private String summary;

        public Builder deprecated(final Boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        public Builder tags(final List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder parameters(final Set<Parameter> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder security(final List<Map<String, List<String>>> security) {
            this.security = security;
            return this;
        }

        public Builder servers(final List<Server> servers) {
            this.servers = servers;
            return this;
        }

        public Builder callbacks(final Map<String, Path> callbacks) {
            this.callbacks = callbacks;
            return this;
        }

        public Builder externalDocs(final ExternalDocumentation externalDocs) {
            this.externalDocs = externalDocs;
            return this;
        }

        public Builder requestBody(final RequestBody requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public Builder responses(final Map<String, ResponseObject> responses) {
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
