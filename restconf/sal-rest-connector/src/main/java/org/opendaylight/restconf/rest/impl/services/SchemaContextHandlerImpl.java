/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class SchemaContextHandlerImpl implements SchemaContextHandler {

    private SchemaContext context;

    @Override
    public void onGlobalContextUpdated(final SchemaContext context) {
        this.context = context;
    }

    @Override
    public SchemaContext getSchemaContext() {
        return this.context;
    }

}
