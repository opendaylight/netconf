/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yang.gen.v1.config.aaa.authn.netconf.plugin.rev150715;

import com.google.common.base.Preconditions;
import org.opendaylight.aaa.odl.CredentialServiceAuthProvider;
import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.api.ModuleIdentifier;
import org.osgi.framework.BundleContext;

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
    public AutoCloseable createInstance() {
       return new CredentialServiceAuthProvider(bundleContext);
    }

}
