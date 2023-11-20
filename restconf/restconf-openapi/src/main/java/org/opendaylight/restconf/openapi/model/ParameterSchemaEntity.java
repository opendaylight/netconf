/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public record ParameterSchemaEntity(@NonNull String type, @Nullable List<String> schemaEnum) {
    public ParameterSchemaEntity(final String type, final List<String> schemaEnum) {
        this.type = type;
        this.schemaEnum = schemaEnum;
    }
}
