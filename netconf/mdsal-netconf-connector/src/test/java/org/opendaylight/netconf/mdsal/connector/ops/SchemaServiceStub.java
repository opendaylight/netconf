/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.concepts.AbstractListenerRegistration;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

final class SchemaServiceStub implements DOMSchemaService {
    private final SchemaContext schemaContext;

    SchemaServiceStub(SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    @Override
    public SchemaContext getSessionContext() {
        return schemaContext;
    }

    @Override
    public SchemaContext getGlobalContext() {
        return schemaContext;
    }

    @Override
    public ListenerRegistration<SchemaContextListener> registerSchemaContextListener(
        final SchemaContextListener listener) {
        listener.onGlobalContextUpdated(getGlobalContext());
        return new AbstractListenerRegistration<SchemaContextListener>(listener) {
            @Override
            protected void removeRegistration() {
                // No-op
            }
        };
    }
}
