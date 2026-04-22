/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model.security;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public record Oauth2(String flow, String authorizationUrl, String tokenUrl, @Nullable String refreshUrl)
        implements SecuritySchemeObject {
    private static final Type TYPE = Type.oauth2;

    public Oauth2 {
        requireNonNull(flow);
        requireNonNull(authorizationUrl);
        requireNonNull(tokenUrl);
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "Oauth2[type=" + TYPE
            + ", flow=" + flow
            + ", authorizationUrl=" + authorizationUrl
            + ", tokenUrl=" + tokenUrl
            + ", refreshUrl=" + refreshUrl + "]";
    }
}
