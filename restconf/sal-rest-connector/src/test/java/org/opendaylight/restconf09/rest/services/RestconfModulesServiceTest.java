/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf09.rest.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceImpl;
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
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        modulesService = new RestconfModulesServiceImpl(contextHandler);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

    @Test
    public void getModuleTest() throws Exception {
        final NormalizedNodeContext module = modulesService.getModule(TEST_MODULE, null);

        assertNotNull(module);

        assertEquals("module1", module.getData().getNodeType().getLocalName());
        assertEquals("2014-01-01", module.getData().getNodeType().getFormattedRevision());
        assertEquals("module:1", module.getData().getNodeType().getNamespace().toString());

        assertEquals("QNameModule{ns=module:1, rev=2014-01-01}", module.getData().getNodeType().getModule().intern());
        assertEquals("2014-01-01", module.getData().getNodeType().getModule().getFormattedRevision());
        assertEquals("module:1", module.getData().getNodeType().getModule().getNamespace().toString());

        assertEquals(1, module.getInstanceIdentifierContext().getSchemaContext().getModules().size());
    }

    @Test(expected=RestconfDocumentedException.class)
    public void getModuleNegativeTest() throws Exception {
        final NormalizedNodeContext module = modulesService.getModule(NOT_EXISTING_MODULE, null);
    }

    @Test
    public void getModulesTest() throws Exception {
        final NormalizedNodeContext modules = modulesService.getModules(TEST_MODULE, null);

        assertNotNull(modules);

        assertEquals("module1", modules.getData().getNodeType().getLocalName());
        assertEquals("2014-01-01", modules.getData().getNodeType().getFormattedRevision());
        assertEquals("module:1", modules.getData().getNodeType().getNamespace().toString());

        assertEquals("QNameModule{ns=module:1, rev=2014-01-01}", modules.getData().getNodeType().getModule().intern());
        assertEquals("2014-01-01", modules.getData().getNodeType().getModule().getFormattedRevision());
        assertEquals("module:1", modules.getData().getNodeType().getModule().getNamespace().toString());

        assertEquals(9, modules.getInstanceIdentifierContext().getSchemaContext().getModules().size());
    }

    @Test(expected=RestconfDocumentedException.class)
    public void getModulesNegativeTest() throws Exception {
        final NormalizedNodeContext modules = modulesService.getModules(NOT_EXISTING_MODULE, null);
    }
}
