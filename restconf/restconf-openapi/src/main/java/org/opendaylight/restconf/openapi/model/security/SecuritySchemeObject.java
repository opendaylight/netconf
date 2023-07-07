/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model.security;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public interface SecuritySchemeObject {
    Type getType();

    enum Type {
        http
        // https://swagger.io/specification/#security-scheme-object specifies valid type values:
        // "apiKey", "http" "mutualTLS", "oauth2", "openIdConnect",
        // we are only using the "http" (which might change in the future => extend the Type enum)
    }
}
