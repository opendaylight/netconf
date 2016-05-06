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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

/**
 * Tests of the {@link RestconfModulesService}
 *
 */
public class RestconfModulesServiceTest {
    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";

    private static final List<String> allowedKeywords = Arrays.asList(new String[] {
            "name", "revision", "namespace", "feature"});

    // service under test
    private RestconfModulesService modulesService;

    // schema context with correct Restconf module
    private SchemaContext schemaContextWithRestconfModule;

    // schema context without Restconf module at all
    private SchemaContext schemaContextWithoutRestconfModule;

    // schema contexts with Restconf module, but:
    // 1. this module does not contain container modules but list modules
    private SchemaContext schemaContextWithNoModulesContainer;
    // 2. this module does not contain list module but leaf-list module
    private SchemaContext schemaContextWithNoModuleList;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private SchemaContextHandler handler;

    @Before
    public void setup() throws ReactorException, FileNotFoundException, URISyntaxException {
        schemaContextWithRestconfModule = TestRestconfUtils.loadSchemaContext("/modules");
        schemaContextWithoutRestconfModule = TestRestconfUtils.loadSchemaContext
                ("/modules/modules-without-restconf-module");
        schemaContextWithNoModulesContainer = TestRestconfUtils.loadSchemaContext
                ("/modules/restconf-module-no-modules-container");
        schemaContextWithNoModuleList = TestRestconfUtils.loadSchemaContext
                ("/modules/restconf-module-no-module-list");

        MockitoAnnotations.initMocks(this);
        modulesService = new RestconfModulesServiceImpl(handler);
    }

    // POSITIVE TESTS

    /**
     * Test non-null init of {@link RestconfModulesServiceImpl}.
     */
    @Test
    public void restconfModulesServiceImpl() {
        assertNotNull("Modules service should be initialized and not null", modulesService);
    }

    /**
     * Test getting all modules supported by the server.
     */
    @Test
    public void getModulesTest() throws Exception {
        setSchemaContextWithRestconfModule();

        NormalizedNodeContext nodeContext = modulesService.getModules(null);
        assertNotNull("Node context cannot be null", nodeContext);

        Collection<MapEntryNode> modules = ((Collection<MapEntryNode>) ((DataContainerChild) ((ContainerNode) nodeContext
                .getData()).getValue().iterator().next()).getValue());

        // check if expected modules were loaded, use module name as map key
        Map<String, String> expectedModules = new HashMap<>();
        for (MapEntryNode node : modules) {
            Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet().iterator();
            String name = null;
            String revision = null;

            while (mapEntries.hasNext()) {
                Map.Entry e = ((AbstractMap.SimpleImmutableEntry) mapEntries.next());
                String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                switch (key) {
                    case "name":
                        name = (String) ((LeafNode) e.getValue()).getValue();
                        break;
                    case "revision":
                        revision = (String) ((LeafNode) e.getValue()).getValue();
                        break;
                    case "namespace":
                    case "feature":
                        break;
                }
            }
            expectedModules.put(name, revision);
        }

        verifyModules(handler.getSchemaContext().getModules(), expectedModules);
    }

    /**
     * Test getting all modules supported by the mount point.
     */
    @Ignore
    @Test
    public void getModulesFromMountPointTest() {}

    /**
     * Test getting the specific module supported by the server.
     */
    @Test
    public void getModuleTest() throws Exception {
        setSchemaContextWithRestconfModule();

        NormalizedNodeContext nodeContext = modulesService.getModule(TEST_MODULE, null);
        assertNotNull("Node context cannot be null", nodeContext);

        MapEntryNode node = ((MapNode) nodeContext.getData()).getValue().iterator().next();
        Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet().iterator();

        while (mapEntries.hasNext()) {
            Map.Entry e = ((AbstractMap.SimpleImmutableEntry) mapEntries.next());
            String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

            assertTrue("Not allowed keyword", allowedKeywords.contains(key));

            switch (key) {
                case "name":
                    assertEquals("Not correct module was found", "module1", ((LeafNode) e.getValue()).getValue());
                    break;
                case "revision":
                    assertEquals("Not correct module was found", "2014-01-01", ((LeafNode) e.getValue()).getValue());
                    break;
                case "namespace":
                    assertEquals("Not correct module was found", "module:1", ((LeafNode) e.getValue()).getValue());
                    break;
                case "feature":
                    assertEquals("Not correct module was found",
                            Collections.emptySet(),((LeafSetNode) e.getValue()).getValue());
                    break;
            }
        }
    }

    /**
     * Test getting the specific module supported by the mount point.
     */
    @Ignore
    @Test
    public void getModuleFromMountPointTest() {}

    // NEGATIVE TESTS

    /**
     * Test getting all modules supported by the server if restconf modules does not exist.
     */
    @Test
    public void getModulesWithoutRestconfModule() {
        setSchemaContextWithoutRestconfModule();

        thrown.expect(NullPointerException.class);
        modulesService.getModules(null);
    }

    /**
     * Test getting all modules supported by the server when restconf module??? is not {@link ContainerSchemaNode}
     */
    @Test
    public void getModulesRestconfModuleWithoutContSchemaNode() {
        setSchemaContextWithNoModulesContainer();

        thrown.expect(IllegalStateException.class);
        modulesService.getModules(null);
    }

    /**
     * Test getting all modules supported by the mount point with null value of
     * identifier for mount point.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getModulesFromMountPointWithNullIdentifier() {}

    /**
     * Test getting all modules supported by the mount point if identifier does
     * not contains {@link RestconfConstants#MOUNT}. Catching
     * {@link RestconfDocumentedException} and testing {@link ErrorType} and
     * {@link ErrorTag}.
     */
    @Ignore
    @Test
    public void getModulesFromMountPointWithoutMountIdentifierOnEndPathIdentifier() {}

    /**
     * Test getting all modules supported by the mount point if restconf modules
     * does not exit.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getModulesFromMountPointWithoutRestconfModule() {}

    /**
     * Test getting all modules supported by the mount point - restconf module
     * is not {@link ContainerSchemaNode}
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void getModulesFromMountPointRestconfModuleWithoutContSchemaNode() {}

    /**
     * Testing getting specific module supported by the server with null
     * identifier param.
     */
    @Test
    public void getModuleWithNullIdentifier() {
        setSchemaContextWithRestconfModule();

        thrown.expect(NullPointerException.class);
        modulesService.getModule(null, null);
    }

    /**
     * Testing getting specific module supported by the server with module which
     * does not exist in schema. Catching {@link RestconfDocumentedException}
     * and testing {@link ErrorType} and {@link ErrorTag}.
     */
    @Test
    public void getModuleModuleDoesNotExist() {
        setSchemaContextWithRestconfModule();

        try {
            modulesService.getModule(NOT_EXISTING_MODULE, null);
            fail("Test should fail due to looking for not-existing module");
        } catch (RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.UNKNOWN_ELEMENT, e.getErrors().get(0).getErrorTag());
            assertEquals("Error code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test getting specific module supported by the server - restconf module is
     * not {@link ListSchemaNode}.
     */
    @Test
    public void getModuleRestconfModuleWithoutListSchemaNode() {
        setSchemaContextWithNoModuleList();

        thrown.expect(IllegalStateException.class);
        // thrown on line 102??
        modulesService.getModule(TEST_MODULE, null);
    }

    /**
     * Testing getting specific module supported by the mount point with module
     * which does not exist in schema. Catching
     * {@link RestconfDocumentedException} and testing {@link ErrorType} and
     * {@link ErrorTag}.
     */
    @Ignore
    @Test
    public void getModuleFromMountPointModuleDoesNotExist() {}

    /**
     * Test getting specific module supported by the mount point - restconf
     * module is not {@link ListSchemaNode}.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void getModuleFromMountPointRestconfModuleWithoutListSchemaNode() {}

    // TEST UTILS - allow changing returned schema context according to situation
    private void setSchemaContextWithRestconfModule() {
        when(handler.getSchemaContext()).thenReturn(schemaContextWithRestconfModule);
    }

    private void setSchemaContextWithoutRestconfModule() {
        when(handler.getSchemaContext()).thenReturn(schemaContextWithoutRestconfModule);
    }

    private void setSchemaContextWithNoModulesContainer() {
        when(handler.getSchemaContext()).thenReturn(schemaContextWithNoModulesContainer);
    }

    private void setSchemaContextWithNoModuleList() {
        when(handler.getSchemaContext()).thenReturn(schemaContextWithNoModuleList);
    }

    /**
     * Verify if correct modules were loaded into Restconf module by comparison with modules from <code>SchemaContext</code>
     * @param expectedModules Modules from <code>SchemaContext</code>
     * @param loadedModules Loaded modules into Restconf module
     */
    private void verifyModules(Set<Module> expectedModules, Map<String, String> loadedModules) {
        assertEquals("Number of loaded modules is not as expected", expectedModules.size(), loadedModules.size());
        for (Module m : expectedModules) {
            String name = m.getName();

            String revision = loadedModules.get(name);
            assertNotNull("Expected module not found", revision);
            assertEquals("Not correct revision of loaded module",
                    SimpleDateFormatUtil.getRevisionFormat().format(m.getRevision()), revision);

            loadedModules.remove(name);
        }
    }
}
