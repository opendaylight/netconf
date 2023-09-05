/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestBody(
        String description,
        ObjectNode content,
        boolean required) {

    private RequestBody(final Builder builder) {
        this(builder.description, builder.content, builder.required);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String description;
        private ObjectNode content;
        private boolean required;

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder content(final ObjectNode content) {
            this.content = content;
            return this;
        }

        public Builder required(final boolean required) {
            this.required = required;
            return this;
        }

        public RequestBody build() {
            return new RequestBody(this);
        }
    }
}
