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
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public record Components(Map<String, Schema> schemas, SecuritySchemes securitySchemes) {
    private Components(Builder builder) {
        this(builder.schemas, builder.securitySchemes);
    }

    public static class Builder {
        private Map<String, Schema> schemas;
        private SecuritySchemes securitySchemes;

        public Builder setSchemas(Map<String, Schema> schemas) {
            this.schemas = schemas;
            return this;
        }

        public Builder setSecuritySchemes(SecuritySchemes securitySchemes) {
            this.securitySchemes = securitySchemes;
            return this;
        }

        public Components build() {
            return new Components(this);
        }
    }
}
