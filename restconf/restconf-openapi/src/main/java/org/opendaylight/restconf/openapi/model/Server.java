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
import org.eclipse.jdt.annotation.NonNull;

/**
 * Represents a Server. The target host is indicated by the {@link Server#url} field.
 *
 * <p>
 * In the example URL: <b>http://localhost:8181/openapi/api/v3/mounts/{id}</b>,
 *
 * @param url <b>Required</b> {@link String} representing the URL to the target host.
 */
@JsonInclude(Include.NON_NULL)
public record Server(
        @NonNull String url) {

    public Server {
        requireNonNull(url);
    }
}
