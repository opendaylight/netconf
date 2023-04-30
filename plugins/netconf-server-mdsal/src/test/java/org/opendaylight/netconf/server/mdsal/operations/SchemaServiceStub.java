/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaServiceExtension;
import org.opendaylight.yangtools.concepts.AbstractListenerRegistration;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;

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
    public ListenerRegistration<EffectiveModelContextListener> registerSchemaContextListener(
        final EffectiveModelContextListener listener) {
        listener.onModelContextUpdated(schemaContext);
        return new AbstractListenerRegistration<>(listener) {
            @Override
            protected void removeRegistration() {
                // No-op
            }
        };
    }

    @Override
    public ClassToInstanceMap<DOMSchemaServiceExtension> getExtensions() {
        return ImmutableClassToInstanceMap.of();
    }
}
