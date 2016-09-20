/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.config.netconf.northbound.impl;

import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;
import java.lang.reflect.Method;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.netconf.api.NetconfServerDispatcher;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.osgi.framework.BundleContext;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class NetconfServerDispatcherModule extends AbstractNetconfServerDispatcherModule {

    private BundleContext bundleContext;

    public NetconfServerDispatcherModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NetconfServerDispatcherModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, NetconfServerDispatcherModule oldModule, AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public AutoCloseable createInstance() {
        final WaitingServiceTracker<NetconfServerDispatcher> tracker =
                WaitingServiceTracker.create(NetconfServerDispatcher.class, bundleContext, "(type=netconf-server-dispatcher)");
        final NetconfServerDispatcher service = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);

        return Reflection.newProxy(AutoCloseableNetconfServerDispatcher.class, new AbstractInvocationHandler() {
            @Override
            protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("close")) {
                    tracker.close();
                    return null;
                } else {
                    return method.invoke(service, args);
                }
            }
        });
    }

    void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    private static interface AutoCloseableNetconfServerDispatcher extends NetconfServerDispatcher, AutoCloseable {
    }
}
