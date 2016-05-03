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
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfModulesServiceTest {
    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";

    @Mock
    private SchemaContextHandler contextHandler;

    private SchemaContext schemaContext;
    private RestconfModulesService modulesService;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        modulesService = new RestconfModulesServiceImpl(contextHandler);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

    @Test
    public void getModuleTest() {
        final NormalizedNodeContext module = modulesService.getModule(TEST_MODULE, null);
        assertNotNull(module);

        assertEquals("Looking for module list", Draft11.RestconfModule.MODULE_LIST_SCHEMA_NODE,
                module.getData().getNodeType().getLocalName());

        final QNameModule restconfModule = module.getData().getNodeType().getModule();
        assertNotNull("Restconf module should be found", restconfModule);

        assertEquals("Expected correct Restconf module revision",
                Draft11.RestconfModule.REVISION, restconfModule.getFormattedRevision());
        assertEquals("Expected correct Restconf module namespace",
                Draft11.RestconfModule.NAMESPACE, restconfModule.getNamespace().toString());
    }

    @Test
    public void getModuleNegativeTest() {
        try {
            modulesService.getModule(NOT_EXISTING_MODULE, null);
            fail("Test sholud fail due to missing not-existing module");
        } catch (RestconfDocumentedException e) {
            assertEquals(400, e.getStatus().getStatusCode());
        }
    }

    @Test
    public void getModulesTest() {
        final NormalizedNodeContext modules = modulesService.getModules(null);
        assertNotNull(modules);

        assertEquals("Looking for modules container", Draft11.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE,
                modules.getData().getNodeType().getLocalName());

        final QNameModule restconfModule = modules.getData().getNodeType().getModule();
        assertNotNull("Restconf module should be found", restconfModule);

        assertEquals("Expected correct Restconf module revision",
                Draft11.RestconfModule.REVISION, restconfModule.getFormattedRevision());
        assertEquals("Expected correct Restconf module namespace",
                Draft11.RestconfModule.NAMESPACE, restconfModule.getNamespace().toString());
    }

    @Ignore
    public void getModulesMountTest() {
        // FIXME should implements test for finding modules behind mount-point
    }
}
