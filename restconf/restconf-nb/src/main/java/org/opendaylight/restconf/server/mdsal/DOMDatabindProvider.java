/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static com.google.common.base.Verify.verifyNotNull;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * A {@link DatabindProvider} monitoring a {@link DOMSchemaService}.
 */
@Singleton
@Component(service = DatabindProvider.class)
public final class DOMDatabindProvider implements DatabindProvider, EffectiveModelContextListener, AutoCloseable {
    private final Registration reg;

    private volatile DatabindContext currentContext;

    @Inject
    @Activate
    public DOMDatabindProvider(@Reference final DOMSchemaService schemaService) {
        currentContext = DatabindContext.ofModel(schemaService.getGlobalContext());
        reg = schemaService.registerSchemaContextListener(this);
    }

    @Override
    public DatabindContext currentContext() {
        return verifyNotNull(currentContext, "Provider already closed");
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        final var local = currentContext;
        if (local != null && local.modelContext() != newModelContext) {
            currentContext = DatabindContext.ofModel(newModelContext);
        }
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
        currentContext = null;
    }
}
