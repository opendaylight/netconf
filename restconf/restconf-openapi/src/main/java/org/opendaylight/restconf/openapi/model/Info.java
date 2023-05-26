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

@JsonInclude(Include.NON_NULL)
public record Info(String version, String title) {

    private Info(final Builder builder) {
        this(builder.version, builder.title);
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Builder {
        private String version;
        private String title;

        public Builder version(final String version) {
            this.version = version;
            return this;
        }

        public Builder title(final String title) {
            this.title = title;
            return this;
        }

        public Info build() {
            return new Info(this);
        }
    }
}
