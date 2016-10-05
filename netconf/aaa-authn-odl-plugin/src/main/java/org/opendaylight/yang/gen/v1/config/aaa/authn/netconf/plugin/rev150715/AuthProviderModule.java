/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yang.gen.v1.config.aaa.authn.netconf.plugin.rev150715;

import com.google.common.base.Preconditions;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;
import java.lang.reflect.Method;
import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.api.ModuleIdentifier;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.netconf.auth.AuthProvider;
import org.osgi.framework.BundleContext;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class AuthProviderModule extends org.opendaylight.yang.gen.v1.config.aaa.authn.netconf.plugin.rev150715.AbstractAuthProviderModule {

    private BundleContext bundleContext;

    public AuthProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public AuthProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.config.aaa.authn.netconf.plugin.rev150715.AuthProviderModule oldModule, AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    public AuthProviderModule(final ModuleIdentifier moduleIdentifier, final DependencyResolver dependencyResolver, final AuthProviderModule oldModule, final AutoCloseable oldInstance, final BundleContext bundleContext) {
        this(moduleIdentifier, dependencyResolver, oldModule, oldInstance);
        this.bundleContext = bundleContext;
    }

    public AuthProviderModule(final ModuleIdentifier moduleIdentifier, final DependencyResolver dependencyResolver, final BundleContext bundleContext) {
        this(moduleIdentifier, dependencyResolver);
        this.bundleContext = bundleContext;
    }

    @Override
    public void customValidation() {
        Preconditions.checkNotNull(bundleContext, "BundleContext was not properly set up");
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final WaitingServiceTracker<AuthProvider> tracker =
                WaitingServiceTracker.create(AuthProvider.class, bundleContext, "(type=netconf-auth-provider)");
        final AuthProvider service = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);

        return Reflection.newProxy(AutoCloseableAuthProvider.class, new AbstractInvocationHandler() {
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

    private static interface AutoCloseableAuthProvider extends AuthProvider, AutoCloseable {
    }
}
