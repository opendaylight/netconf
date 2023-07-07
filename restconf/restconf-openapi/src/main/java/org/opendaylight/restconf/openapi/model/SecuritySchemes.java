/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static com.google.common.base.Verify.verifyNotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

@JsonInclude(Include.NON_NULL)
public record SecuritySchemes(SecurityObject basicAuth) {

    /**
     * This record serves as a Security Scheme Object as defined in
     * <a href=https://swagger.io/specification/#security-scheme-object>Swagger Specification</a> for HTTP type.
     *
     * <p>
     * Only fields, supported by <b>HTTP</b> Security Scheme Object type, are implemented
     *
     * @param type type of security scheme object
     * @param description description for security scheme
     * @param scheme name of the HTTP Authorization scheme to be used in the Authorization header
     * @param bearerFormat hint to the client to identify how the bearer token is formatted
     */
    @JsonInclude(Include.NON_NULL)
    public record SecurityObject(
        @NonNull Type type,
        @Nullable String description,
        @NonNull String scheme,
        @Nullable String bearerFormat) {

        private SecurityObject(final Builder builder) {
            this(builder.type, builder.description, builder.scheme, builder.bearerFormat);
        }

        @SuppressWarnings("checkstyle:hiddenField")
        public static class Builder {
            private Type type;
            private String description;
            private String scheme;
            private String bearerFormat;

            public Builder type(final Type type) {
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

            public SecurityObject build() {
                verifyNotNull(type, "The Security Scheme Object's type must not be null");
                verifyNotNull(scheme, "The Security Scheme Object's scheme must not be null");
                return new SecurityObject(this);
            }
        }
    }

    public enum Type {
        http
        // https://swagger.io/specification/#security-scheme-object specifies valid type values:
        // "apiKey", "http" "mutualTLS", "oauth2", "openIdConnect",
        // we are only using the "http" (which might change in the future => extend the Type enum)
    }
}
