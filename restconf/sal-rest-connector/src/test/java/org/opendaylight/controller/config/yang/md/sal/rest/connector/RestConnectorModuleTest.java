/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.md.sal.rest.connector;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.api.ModuleIdentifier;
import org.opendaylight.controller.sal.core.api.Broker;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;

public class RestConnectorModuleTest {

    @Test
    public void rcmInit1Tes() {
        final DependencyResolver dependencyResolver = mock(DependencyResolver.class);
        final ModuleIdentifier identifier = mock(ModuleIdentifier.class);
        final RestConnectorModule rcm = new RestConnectorModule(identifier, dependencyResolver);
        assertNotNull(rcm);
    }

    @Test
    public void rcmInit2Tes() {
        final DependencyResolver dependencyResolver = mock(DependencyResolver.class);
        final ModuleIdentifier identifier = mock(ModuleIdentifier.class);
        final RestConnectorModule oldModule = mock(RestConnectorModule.class);
        final AutoCloseable oldInstance = mock(AutoCloseable.class);
        final RestConnectorModule rcm = new RestConnectorModule(identifier, dependencyResolver, oldModule, oldInstance);
        assertNotNull(rcm);
    }

    @Ignore
    @Test
    public void createInstanceTest() throws Exception {
        final DependencyResolver dependencyResolver = mock(DependencyResolver.class);
        final ModuleIdentifier identifier = mock(ModuleIdentifier.class);
        final RestConnectorModule rcm = new RestConnectorModule(identifier, dependencyResolver);

        final BundleContext bundleContext = mock(BundleContext.class);
        final Filter filter = mock(Filter.class);

        final Broker broker = mock(Broker.class);
        setInnerField(rcm, "domBrokerDependency", broker);

        final RestConnectorRuntimeRegistration runtimeReg = mock(RestConnectorRuntimeRegistration.class);
        setField(rcm, "runtimeRegistration", runtimeReg);

        final RestConnectorRuntimeRegistrator registrator = mock(RestConnectorRuntimeRegistrator.class);
        setInnerField(rcm, "rootRuntimeBeanRegistratorWrapper", registrator);
        rcm.setBundleContext(bundleContext);
        when(bundleContext.createFilter("(objectClass=org.opendaylight.aaa.api.AAAService)")).thenReturn(filter);

        final AutoCloseable instance = rcm.createInstance();
        assertNotNull(instance);
    }

    private static void setInnerField(final RestConnectorModule rcm, final String name, final Object value)
            throws Exception {
        final Field declaredField = rcm.getClass().getSuperclass().getDeclaredField(name);
        declaredField.setAccessible(true);
        declaredField.set(rcm, value);
    }

    private static void setField(final RestConnectorModule rcm, final String name, final Object value)
            throws Exception {
        final Field declaredField = rcm.getClass().getDeclaredField(name);
        declaredField.setAccessible(true);
        declaredField.set(rcm, value);
    }
}
