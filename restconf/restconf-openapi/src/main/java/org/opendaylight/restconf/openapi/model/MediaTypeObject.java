/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.jdt.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaTypeObject(
        @Nullable Schema schema) {

    private MediaTypeObject(final Builder builder) {
        this(builder.schema);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        Schema schema;

        public Builder schema(Schema schema) {
            this.schema = schema;
            return this;
        }

        public MediaTypeObject build() {
            return new MediaTypeObject(this);
        }
    }
}
