/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.swagger;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SecuritySchemes {
    private static final ObjectNode BASIC_AUTH = JsonNodeFactory.instance.objectNode()
            .put("type", "http")
            .put("scheme", "basic");

    public SecuritySchemes() {
        // no-op
    }

    public ObjectNode getBasicAuth() {
        return BASIC_AUTH;
    }

    public void setBasicAuth(ObjectNode basicAuth) {
        // no-op
    }
}
