/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.openapi;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class SecurityDefinitions {
    private ObjectNode basicAuth;

    public SecurityDefinitions(ObjectNode basicAuth) {
        this.basicAuth = basicAuth;
    }

    public ObjectNode getBasicAuth() {
        return basicAuth;
    }

    public void setBasicAuth(ObjectNode basicAuth) {
        this.basicAuth = basicAuth;
    }
}
