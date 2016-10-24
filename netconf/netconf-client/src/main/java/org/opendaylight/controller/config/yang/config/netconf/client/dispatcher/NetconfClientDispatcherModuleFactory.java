/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.config.netconf.client.dispatcher;

import org.opendaylight.controller.config.api.DependencyResolver;
import org.osgi.framework.BundleContext;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class NetconfClientDispatcherModuleFactory extends AbstractNetconfClientDispatcherModuleFactory {
    @Override
    public NetconfClientDispatcherModule instantiateModule(String instanceName, DependencyResolver dependencyResolver,
                                                           NetconfClientDispatcherModule oldModule, AutoCloseable oldInstance, BundleContext bundleContext) {
        NetconfClientDispatcherModule module = super.instantiateModule(instanceName, dependencyResolver, oldModule,
                oldInstance, bundleContext);
        module.setBundleContext(bundleContext);
        return module;
    }

    @Override
    public NetconfClientDispatcherModule instantiateModule(String instanceName, DependencyResolver dependencyResolver,
                                                      BundleContext bundleContext) {
        NetconfClientDispatcherModule module = super.instantiateModule(instanceName, dependencyResolver, bundleContext);
        module.setBundleContext(bundleContext);
        return module;
    }
}
