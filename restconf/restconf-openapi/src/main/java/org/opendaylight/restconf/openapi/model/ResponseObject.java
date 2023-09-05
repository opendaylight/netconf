/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseObject(
        @NonNull String description,
        @Nullable Map<String, String> headers,
        @Nullable Map<String, MediaTypeObject> content,
        @Nullable Map<String, Link> links) {

    public ResponseObject {
        description = Objects.requireNonNull(description);
        headers = headers == null ? null : Map.copyOf(headers);
        content = content == null ? null : Map.copyOf(content);
        links = links == null ? null : Map.copyOf(links);
    }

    private ResponseObject(final Builder builder) {
        this(builder.description, builder.headers, builder.content, builder.links);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String description;
        private Map<String, String> headers;
        private Map<String, MediaTypeObject> content;
        private Map<String, Link> links;

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder headers(final Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder content(final Map<String, MediaTypeObject> content) {
            this.content = content;
            return this;
        }

        public Builder links(final Map<String, Link> links) {
            this.links = links;
            return this;
        }

        public ResponseObject build() {
            return new ResponseObject(this);
        }
    }
}
