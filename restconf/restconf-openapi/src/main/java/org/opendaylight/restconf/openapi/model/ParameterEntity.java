/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public record ParameterEntity(@NonNull String name, @NonNull String in, boolean required,
        @NonNull ParameterSchemaEntity schema, @Nullable String description) {
    public ParameterEntity(final String name, final String in, final boolean required,
            final ParameterSchemaEntity schema, final String description) {
        this.name = name;
        this.in = in;
        this.required = required;
        this.schema = schema;
        this.description = description;
    }
}
