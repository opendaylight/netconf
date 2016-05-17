/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import org.opendaylight.controller.sal.core.api.Broker.ProviderSession;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.sal.rest.api.RestConnector;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * Provider for restconf draft11.
 *
 */
public class RestConnectorProvider implements Provider, RestConnector, AutoCloseable {

    private ListenerRegistration<SchemaContextListener> listenerRegistration;

    @Override
    public void onSessionInitiated(final ProviderSession session) {
        final SchemaService schemaService = Preconditions.checkNotNull(session.getService(SchemaService.class));
        final RestconfApplication restApp = getObjectFromBundleContext(RestconfApplication.class,
                RestconfApplicationService.class.getName());
        Preconditions.checkNotNull(restApp, "RestconfApplication service doesn't exist.");
        this.listenerRegistration = schemaService.registerSchemaContextListener(restApp.getSchemaContextHandler());
    }

    private <T> T getObjectFromBundleContext(final Class<T> type, final String serviceRefName) {
        final BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        final ServiceReference<?> serviceReference = bundleContext.getServiceReference(serviceRefName);
        return (T) bundleContext.getService(serviceReference);
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public void close() throws Exception {
        if (this.listenerRegistration != null) {
            this.listenerRegistration.close();
        }
    }
}
