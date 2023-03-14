/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.openapi;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;

public class Components {

    private final Map<String, ObjectNode> schemas;
    private final SecuritySchemes securitySchemes;

    public Components(final SecuritySchemes securitySchemes) {
        this.securitySchemes = securitySchemes;
        this.schemas = new HashMap<>();
    }

    public Map<String, ObjectNode> getSchemas() {
        return schemas;
    }

    public void setSchemas(final Map<String, ObjectNode> newSchema) {
        schemas.clear();
        schemas.putAll(newSchema);
    }

    public SecuritySchemes getSecuritySchemes() {
        return securitySchemes;
    }
}
