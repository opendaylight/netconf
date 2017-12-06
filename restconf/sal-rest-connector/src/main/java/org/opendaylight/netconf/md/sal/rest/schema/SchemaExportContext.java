/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.md.sal.rest.schema;

import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class SchemaExportContext {

    private final SchemaContext schemaContext;
    private final Module module;
    private final DOMYangTextSourceProvider sourceProvider;

    public SchemaExportContext(final SchemaContext ctx, final Module module,
            final DOMYangTextSourceProvider sourceProvider) {
        schemaContext = ctx;
        this.module = module;
        this.sourceProvider = sourceProvider;
    }

    public SchemaContext getSchemaContext() {
        return schemaContext;
    }

    public Module getModule() {
        return module;
    }

    public DOMYangTextSourceProvider getSourceProvider() {
        return sourceProvider;
    }
}
