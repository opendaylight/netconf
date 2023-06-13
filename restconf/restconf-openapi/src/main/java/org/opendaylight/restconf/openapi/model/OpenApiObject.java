/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public record OpenApiObject(String openapi, Info info, List<Server> servers, Map<String, Path> paths,
                            Components components, ArrayNode security) {

    private OpenApiObject(final Builder builder) {
        this(builder.openapi, builder.info, builder.servers, builder.paths, builder.components, builder.security);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String openapi;
        private Info info;
        private List<Server> servers;
        private Map<String, Path> paths;
        private Components components;
        private ArrayNode security;

        public Builder openapi(final String openapi) {
            this.openapi = openapi;
            return this;
        }

        public Builder info(final Info info) {
            this.info = info;
            return this;
        }

        public Builder servers(final List<Server> servers) {
            this.servers = servers;
            return this;
        }

        public Builder paths(final Map<String, Path> paths) {
            this.paths = paths;
            return this;
        }

        public Builder components(final Components components) {
            this.components = components;
            return this;
        }

        public Builder security(final ArrayNode security) {
            this.security = security;
            return this;
        }

        public OpenApiObject build() {
            return new OpenApiObject(this);
        }

        // FIXME remove this getter
        public Components getComponents() {
            return this.components;
        }

        // FIXME remove this getter
        public Map<String, Path> getPaths() {
            return this.paths;
        }
    }
}
