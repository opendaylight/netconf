/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static java.util.Objects.requireNonNull;

import java.util.function.Consumer;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class SchemaServiceStub implements DOMSchemaService {
    private final EffectiveModelContext schemaContext;

    SchemaServiceStub(final EffectiveModelContext schemaContext) {
        this.schemaContext = requireNonNull(schemaContext);
    }

    @Override
    public EffectiveModelContext getGlobalContext() {
        return schemaContext;
    }

    @Override
    public Registration registerSchemaContextListener(final Consumer<EffectiveModelContext> listener) {
        listener.accept(schemaContext);
        return () -> {
            // No-op
        };
    }
}
