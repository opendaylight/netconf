/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind.mdsal;

import static com.google.common.base.Verify.verifyNotNull;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContextProvider;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * A {@link DatabindContextProvider} monitoring a {@link DOMSchemaService}.
 */
@Singleton
@Component(service = DatabindContextProvider.class)
public final class DOMDatabindContextProvider
        implements DatabindContextProvider, EffectiveModelContextListener, AutoCloseable {
    private final Registration reg;

    private volatile DatabindContext currentDatabindContext;

    @Inject
    @Activate
    public DOMDatabindContextProvider(@Reference final DOMSchemaService schemaService) {
        onModelContextUpdated(schemaService.getGlobalContext());
        reg = schemaService.registerSchemaContextListener(this);
    }

    @Override
    public DatabindContext currentDatabindContext() {
        return verifyNotNull(currentDatabindContext, "Provider already closed");
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
        currentDatabindContext = null;
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        currentDatabindContext = DatabindContext.ofModel(newModelContext);
    }
}
