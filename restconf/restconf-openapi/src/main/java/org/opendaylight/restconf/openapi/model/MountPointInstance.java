/*
 * Copyright (c) 2018 ZTE Corporation and others.  All rights reserved.
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
public record MountPointInstance(String instance, Long id) {

    private MountPointInstance(final Builder builder) {
        this(builder.instance, builder.id);
    }

    public static class Builder {
        private String instance;
        private Long id;

        public Builder(Map.Entry<String, Long> entry) {
            this.instance = entry.getKey();
            this.id = entry.getValue();
        }

        public Builder setInstance(String instance) {
            this.instance = instance;
            return this;
        }

        public Builder setId(Long id) {
            this.id = id;
            return this;
        }

        public MountPointInstance build() {
            return new MountPointInstance(this);
        }
    }
}
