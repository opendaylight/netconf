/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseObject(
    String description,
    ObjectNode headers,
    ObjectNode content,
    ObjectNode links) {

    private ResponseObject(Builder builder) {
        this(builder.description, builder.headers, builder.content, builder.links);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String description;
        private ObjectNode headers;
        private ObjectNode content;
        private ObjectNode links;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder headers(ObjectNode headers) {
            this.headers = headers;
            return this;
        }

        public Builder content(ObjectNode content) {
            this.content = content;
            return this;
        }

        public Builder links(ObjectNode links) {
            this.links = links;
            return this;
        }

        public ResponseObject build() {
            return new ResponseObject(this);
        }
    }
}
