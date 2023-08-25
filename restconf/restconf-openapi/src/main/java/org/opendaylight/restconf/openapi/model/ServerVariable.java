/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Record that models server variable object according to openapi specification, section
 * <a href=https://swagger.io/specification/#server-variable-object>server variable object</a>.
 *
 * @param defaultValue required value used for substitution. If {@link ServerVariable#values} array is defined,
 *                     this {@link ServerVariable#defaultValue} must be present in that array.
 * @param values enum of string values to be used for URL template substitution.
 *               May be undefined, but if defined, cannot be empty.
 * @param description description of this server variable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerVariable(
        @NonNull @JsonProperty("default") String defaultValue,
        @Nullable @JsonProperty("enum") List<String> values,
        @Nullable String description) {

    public ServerVariable {
        requireNonNull(defaultValue);
    }
}
