/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;

/**
 * OAuth2/OIDC authorization-code-with-PKCE configuration for the OpenAPI security schemes section.
 * When this record is absent (null), the generated OpenAPI document omits the {@code oauth2} security
 * scheme and only advertises HTTP Basic authentication.
 */
public record OpenApiOauth2Configuration(
        @NonNull String authorizationUrl,
        @NonNull String tokenUrl,
        @NonNull String refreshUrl) {

    public OpenApiOauth2Configuration {
        requireNonNull(authorizationUrl);
        requireNonNull(tokenUrl);
        requireNonNull(refreshUrl);
    }
}
