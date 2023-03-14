/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.openapi;

public class Components {
    private String schemas;
    private SecuritySchemes securitySchemes;

    public Components() {

    }

    public Components(final String schemas, SecuritySchemes securitySchemes) {
        this.schemas = schemas;
        this.securitySchemes = securitySchemes;
    }

    public String getSchemas() {
        return schemas;
    }

    public void setSchemas(String schemas) {
        this.schemas = schemas;
    }

    public SecuritySchemes getSecuritySchemes() {
        return securitySchemes;
    }

    public void setSecuritySchemes(SecuritySchemes securitySchemes) {
        this.securitySchemes = securitySchemes;
    }
}
