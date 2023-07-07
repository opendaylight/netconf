/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BasicAuth(String type, String description, String scheme, String bearerFormat) {

    private BasicAuth(final BasicAuth.Builder builder) {
        this(builder.type, builder.description, builder.scheme, builder.bearerFormat);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String type;
        private String description;
        private String scheme;
        private String bearerFormat;

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder scheme(final String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder bearerFormat(final String bearerFormat) {
            this.bearerFormat = bearerFormat;
            return this;
        }

        public BasicAuth build() {
            return new BasicAuth(this);
        }
    }
}
