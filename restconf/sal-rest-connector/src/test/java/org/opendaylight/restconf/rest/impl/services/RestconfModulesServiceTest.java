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

    /**
     * Test if service was successfully created
     */
    @Test
    public void modulesServiceImplInitTest() {
        assertNotNull(modulesService);
    }

    /**
     * Get module list from Restconf module for existing module
     */
    @Test
    public void getModuleTest() {
        final NormalizedNodeContext module = modulesService.getModule(TEST_MODULE, null);
        assertNotNull(module);
        verifyRestconfModule(module.getData().getNodeType().getModule());
        assertEquals("Looking for module list", Draft11.RestconfModule.MODULE_LIST_SCHEMA_NODE,
                module.getData().getNodeType().getLocalName());
    }

    /**
     * Get modules container from Restconf module for all available modules supported by the server
     */
    @Test
    public void getModulesTest() {
        final NormalizedNodeContext modules = modulesService.getModules(null);
        assertNotNull(modules);
        verifyRestconfModule(modules.getData().getNodeType().getModule());
        assertEquals("Looking for modules container", Draft11.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE,
                modules.getData().getNodeType().getLocalName());
    }

    /**
     * Negative test trying to find module list from Restconf module for not-existing module
     */
    @Test
    public void getModuleNegativeTest() {
        try {
            modulesService.getModule(NOT_EXISTING_MODULE, null);
            fail("Test should fail due to missing not-existing module");
        } catch (RestconfDocumentedException e) {
            assertEquals("Error code 400 expected", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    @Ignore
    @Test
    public void getModulesMountTest() {
        // FIXME should implements test for finding modules behind mount-point
    }

    /**
     * Verify Restconf module
     * @param restconfModule
     */
    private void verifyRestconfModule(final QNameModule restconfModule) {
        assertNotNull("Restconf module should be found", restconfModule);

        assertEquals("Expected correct Restconf module revision",
                Draft11.RestconfModule.REVISION, restconfModule.getFormattedRevision());
        assertEquals("Expected correct Restconf module namespace",
                Draft11.RestconfModule.NAMESPACE, restconfModule.getNamespace().toString());
    }
}
