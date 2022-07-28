/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class SchemaExportContext {
    private final EffectiveModelContext schemaContext;
    private final Module module;
    private final DOMYangTextSourceProvider sourceProvider;

    public SchemaExportContext(final EffectiveModelContext schemaContext, final Module module,
                               final DOMYangTextSourceProvider sourceProvider) {
        this.schemaContext = schemaContext;
        this.module = module;
        this.sourceProvider = sourceProvider;
    }

    public EffectiveModelContext getSchemaContext() {
        return schemaContext;
    }

    public Module getModule() {
        return module;
    }

    public DOMYangTextSourceProvider getSourceProvider() {
        return sourceProvider;
    }
}
