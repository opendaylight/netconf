/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Represents a Server. The target host is indicated by the {@link Server#url} field.
 * This URL supports server variables, which are specified in the {@link Server#variables} field
 * and must be enclosed in {brackets} within the URL.
 *
 * <p>
 * In the example URL: <b>http://localhost:8181/openapi/api/v3/mounts/{id}</b>,
 * 'id' represents the variable name (key), and the corresponding ServerVariable (value)
 * contains information about possible values.
 *
 * @param url <b>Required</b> {@link String} representing the URL to the target host.
 * @param description {@link String} describing the host designated by the URL.
 * @param variables {@link Map} of variables used for substitution, where the key is the variable name
 *                             and value is a {@link ServerVariable} object containing relevant information.
 */
@JsonInclude(Include.NON_NULL)
public record Server(
        @NonNull String url,
        @Nullable String description,
        @Nullable Map<String, ServerVariable> variables) {

    public Server {
        requireNonNull(url);
    }
}
