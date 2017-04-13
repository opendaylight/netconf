/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.sal.restconf.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.api.ModuleIdentifier;

public class JSONRestconfServiceModuleTest {

    @Test
    public void jrsmInit1Test() {
        final DependencyResolver dependencyResolver = mock(DependencyResolver.class);
        final ModuleIdentifier identifier = mock(ModuleIdentifier.class);
        final JSONRestconfServiceModule jrsm = new JSONRestconfServiceModule(identifier, dependencyResolver);
        assertNotNull(jrsm);
    }

    @Test
    public void jrsmInit2Test() {
        final DependencyResolver dependencyResolver = mock(DependencyResolver.class);
        final ModuleIdentifier identifier = mock(ModuleIdentifier.class);
        final JSONRestconfServiceModule oldModule = mock(JSONRestconfServiceModule.class);
        final AutoCloseable oldInstance = mock(AutoCloseable.class);
        final JSONRestconfServiceModule jrsm =
                new JSONRestconfServiceModule(identifier, dependencyResolver, oldModule, oldInstance);
        assertNotNull(jrsm);
    }

    @Test
    public void customValidationTest() {
        final DependencyResolver dependencyResolver = mock(DependencyResolver.class);
        final ModuleIdentifier identifier = mock(ModuleIdentifier.class);
        final JSONRestconfServiceModule jrsm = new JSONRestconfServiceModule(identifier, dependencyResolver);
        jrsm.customValidation();
        assertNotNull(jrsm);
    }

    @Test
    public void createInstanceTest() {
        final DependencyResolver dependencyResolver = mock(DependencyResolver.class);
        final ModuleIdentifier identifier = mock(ModuleIdentifier.class);
        final JSONRestconfServiceModule jrsm = new JSONRestconfServiceModule(identifier, dependencyResolver);
        assertNotNull(jrsm);
        final AutoCloseable instance = jrsm.createInstance();
        assertNotNull(instance);
    }
}
