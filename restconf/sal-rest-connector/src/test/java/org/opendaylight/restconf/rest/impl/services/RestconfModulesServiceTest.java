/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest.impl.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@RunWith(MockitoJUnitRunner.class)
public class RestconfModulesServiceTest {
    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";

    @Mock
    private SchemaContextHandler contextHandler;

    private SchemaContext schemaContext;
    private RestconfModulesService modulesService;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        modulesService = new RestconfModulesServiceImpl(contextHandler);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

    @Test
    public void getModuleTest() {
        final NormalizedNodeContext module = modulesService.getModule(TEST_MODULE, null);
        assertNotNull(module);

        assertEquals("module", module.getData().getNodeType().getLocalName());
        final QNameModule restconfModule = module.getData().getNodeType().getModule();
        assertNotNull(restconfModule);

        assertEquals("2013-10-19", restconfModule.getFormattedRevision());
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf", restconfModule.getNamespace().toString());
    }

    @Test(expected = AbstractMethodError.class)
    public void getModuleNegativeTest() {
        modulesService.getModule(NOT_EXISTING_MODULE, null);
    }

    @Test
    public void getModulesTest() {
        final NormalizedNodeContext modules = modulesService.getModules(null);
        assertNotNull(modules);

        assertEquals("modules", modules.getData().getNodeType().getLocalName());
        final QNameModule restconfModule = modules.getData().getNodeType().getModule();
        assertNotNull(restconfModule);

        assertEquals("2013-10-19", restconfModule.getFormattedRevision());
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf", restconfModule.getNamespace().toString());
    }

    @Test
    public void getModulesMountTest() {
        // TODO when method under test will be implemented
    }
}
