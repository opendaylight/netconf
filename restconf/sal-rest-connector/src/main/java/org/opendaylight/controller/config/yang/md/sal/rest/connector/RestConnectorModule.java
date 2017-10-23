/**
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.md.sal.rest.connector;

import org.opendaylight.RestconfWrapperProviders;
import org.opendaylight.aaa.api.AAAService;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.osgi.framework.BundleContext;


public class RestConnectorModule extends org.opendaylight.controller.config.yang.md.sal.rest.connector.AbstractRestConnectorModule {

    private static RestConnectorRuntimeRegistration runtimeRegistration;

    private BundleContext bundleContext;

    public RestConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public RestConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorModule oldModule, final java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {

        final WaitingServiceTracker<AAAService> aaaServiceWaitingServiceTracker =
                WaitingServiceTracker.create(AAAService.class, bundleContext);
        aaaServiceWaitingServiceTracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);

        final RestconfWrapperProviders wrapperProviders = new RestconfWrapperProviders(
                                                                getWebsocketAddress(), getWebsocketPort());
        wrapperProviders.registerProviders(getDomBrokerDependency());

        if(runtimeRegistration != null){
            runtimeRegistration.close();
        }

        runtimeRegistration = wrapperProviders.runtimeRegistration(getRootRuntimeBeanRegistratorWrapper());

        return wrapperProviders;
    }

    public void setBundleContext(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}

