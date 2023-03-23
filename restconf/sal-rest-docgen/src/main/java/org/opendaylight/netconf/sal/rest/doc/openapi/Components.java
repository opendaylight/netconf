/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.openapi;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class Components {
    private ObjectNode schemas;

    public Components() {

    }

    public Components(ObjectNode schemas) {
        this.schemas = schemas;
    }

    public ObjectNode getSchemas() {
        return schemas;
    }

    public void setSchemas(ObjectNode schemas) {
        this.schemas = schemas;
    }
}
