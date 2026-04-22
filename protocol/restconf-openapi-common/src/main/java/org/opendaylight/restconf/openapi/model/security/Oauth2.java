/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model.security;

import org.eclipse.jdt.annotation.NonNull;

public record Oauth2(
        @NonNull String scheme,
        @NonNull String flow,
        @NonNull String authorizationUrl) implements SecuritySchemeObject {
    private static final Type TYPE = Type.oauth2;

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "Oauth2[type=" + TYPE
            + ", scheme=" + scheme
            + ", flow=" + flow
            + ", authorizationUrl=" + authorizationUrl + "]";
    }
}
