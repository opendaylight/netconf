/*
 * Copyright (c) 2019 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesNotifWrapper;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapper;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.restconf.nb.rfc8040.streams.sse.SSEInitializer;
import org.opendaylight.restconf.nb.rfc8040.web.WebInitializer;

/**
 * Standalone wiring for RESTCONF.
 *
 * <p>This wiring alone is not sufficient; there are a few other singletons which
 * need to be bound as well, incl. {@link RestconfApplication}, &amp; {@link WebInitializer}; see the
 * Rfc8040RestConfWiringTest for how to do this e.g. for Guice (this class can
 * be used with another DI framework but needs the equivalent).
 *
 * @author Michael Vorburger.ch
 */
@Singleton
public class Rfc8040RestConfWiring {
    private final ServicesWrapper servicesWrapper;
    private final ServicesNotifWrapper servicesNotifWrapper;

    @Inject
    public Rfc8040RestConfWiring(final SchemaContextHandler schemaCtxHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler,
            final TransactionChainHandler transactionChainHandler,
            final DOMDataBrokerHandler domDataBrokerHandler, final RpcServiceHandler rpcServiceHandler,
            @Reference final DOMActionService actionService,
            final NotificationServiceHandler notificationServiceHandler,
            final SSEInitializer sseInit,
            final Configuration configuration,
            @Reference final DOMSchemaService domSchemaService) {
        servicesWrapper = ServicesWrapper.newInstance(schemaCtxHandler, domMountPointServiceHandler,
            transactionChainHandler, domDataBrokerHandler, rpcServiceHandler, actionService,
            notificationServiceHandler, domSchemaService, configuration);
        servicesNotifWrapper = ServicesNotifWrapper.newInstance(sseInit);
    }

    public ServicesWrapper getServicesWrapper() {
        return servicesWrapper;
    }

    /**
     * Return ServicesNotifWrapper for blueprint inject.
     */
    public ServicesNotifWrapper getServicesNotifWrapper() {
        return servicesNotifWrapper;
    }

}
