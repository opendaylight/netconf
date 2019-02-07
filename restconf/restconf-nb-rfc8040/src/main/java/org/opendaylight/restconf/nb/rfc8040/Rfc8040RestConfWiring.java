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
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.JSONRestconfServiceRfc8040Impl;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapper;
import org.opendaylight.restconf.nb.rfc8040.web.WebInitializer;

/**
 * Standalone wiring for RESTCONF.
 *
 * @author Michael Vorburger.ch
 */
@Singleton
public class Rfc8040RestConfWiring {

    private final ServicesWrapper servicesWrapper;

    @Inject
    public Rfc8040RestConfWiring(
        // These arguments here are required by ServicesWrapper
            SchemaContextHandler schemaCtxHandler,
            DOMMountPointServiceHandler domMountPointServiceHandler, TransactionChainHandler transactionChainHandler,
            DOMDataBrokerHandler domDataBrokerHandler, RpcServiceHandler rpcServiceHandler,
            NotificationServiceHandler notificationServiceHandler, @Reference DOMSchemaService domSchemaService,
        // The point of the following arguments is to make your chosen Dependency Injection (DI) framework init. them
            RestconfApplication restconfApplication, JSONRestconfServiceRfc8040Impl jsonRestconfService,
            WebInitializer webInitializer) {
        servicesWrapper = ServicesWrapper.newInstance(schemaCtxHandler, domMountPointServiceHandler,
                transactionChainHandler, domDataBrokerHandler, rpcServiceHandler, notificationServiceHandler,
                domSchemaService);
    }

    public ServicesWrapper getServicesWrapper() {
        return servicesWrapper;
    }
}
