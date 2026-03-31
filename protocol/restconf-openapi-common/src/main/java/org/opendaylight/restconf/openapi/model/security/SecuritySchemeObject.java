/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model.security;

public interface SecuritySchemeObject {
    Type type();

    /**
     * Security types enumeration.
     *
     * <p><a href="https://swagger.io/specification/#security-scheme-object">OpenApi spec</a> specifies valid type
     * values: "apiKey", "http" "mutualTLS", "oauth2", "openIdConnect".
     *
     * <p>We are only using the "http" which might change in the future, so we will extend the Type enum.
     */
    enum Type {
        http
    }
}
