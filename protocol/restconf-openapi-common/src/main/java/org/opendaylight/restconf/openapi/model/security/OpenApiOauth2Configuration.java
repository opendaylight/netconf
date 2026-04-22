/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model.security;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * OAuth2/OIDC authorization-code-with-PKCE configuration for the OpenAPI security schemes section.
 *
 * <p>When this record is absent (null), the generated OpenAPI document omits the {@code oauth2} security
 * scheme and only advertises HTTP Basic authentication.
 *
 * <p>A valid instance of this record has to contain nonnull and nonblank {@code authorizationUrl}
 * and {@code tokenUrl}.
 */
@NonNullByDefault
public record OpenApiOauth2Configuration(String authorizationUrl, String tokenUrl, @Nullable String refreshUrl) {
    public OpenApiOauth2Configuration {
        requireNonNull(authorizationUrl);
        checkState(!authorizationUrl.isBlank(), "Authorization URL must not be blank");
        requireNonNull(tokenUrl);
        checkState(!tokenUrl.isBlank(), "Token URL must not be blank");
    }
}
