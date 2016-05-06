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
import org.mockito.Spy;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.broker.impl.mount.DOMMountPointServiceImpl;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests of the {@link RestconfModulesService}
 *
 */
public class RestconfModulesServiceTest {
    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";

    private static final String TEST_MODULE_BEHIND_MOUNT_POINT = "";
    private static final String NOT_EXISTING_MODULE_BEHIND_MOUNT_POINT = "";

    private static final List<String> allowedKeywords = Arrays.asList("name", "revision", "namespace", "feature");

    private DOMMountPointService mountPointService = new DOMMountPointServiceImpl();

    // service under test
    private RestconfModulesService modulesService;

    // schema context with correct Restconf module
    @Spy private SchemaContext schemaContext;
    @Spy private SchemaContext schemaContextBehindMountPoint;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock private SchemaContextHandler handler;
    @Mock private DOMMountPointServiceHandler mountPointServiceHandler;
    @Mock private DOMMountPoint mountPoint;

    @Before
    public void setupMocks() {
        MockitoAnnotations.initMocks(this);
    }

    private void setupCustomRestconfModule(final String restconfModulePath) throws Exception {
        schemaContext = TestRestconfUtils.parseYangSource(restconfModulePath);
        when(handler.getSchemaContext()).thenReturn(schemaContext);
        modulesService = new RestconfModulesServiceImpl(handler, null);
    }

    private void setupNormal() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        when(handler.getSchemaContext()).thenReturn(schemaContext);
        modulesService = new RestconfModulesServiceImpl(handler, null);
    }

    private void setupNormalMountPoint() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        when(handler.getSchemaContext()).thenReturn(schemaContext);
        modulesService = new RestconfModulesServiceImpl(handler, null);
    }

    /**
     * Test non-null init of {@link RestconfModulesServiceImpl}.
     */
    @Test
    public void restconfModulesServiceImplTest() {
        assertNotNull("Modules service should be initialized and not null", modulesService);
    }

    /**
     * Test getting all modules supported by the server. Retrieved modules are verified by the name, revision and
     * namespace.
     */
    @Test
    public void getModulesTest() throws Exception {

        final NormalizedNodeContext nodeContext = modulesService.getModules(null);
        assertNotNull("Node context cannot be null", nodeContext);

        final Collection<MapEntryNode> modules = (Collection<MapEntryNode>) ((ContainerNode) nodeContext .getData())
                .getValue().iterator().next().getValue();

        // check if expected modules were loaded, use module name as map key
        final Map<String, String> loadedModules = new HashMap<>();
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

            loadedModules.put(name, revision);
        }

        verifyModules(schemaContext.getModules(), loadedModules);
    }

    /**
     * Test getting all modules supported by the mount point. Retrieved modules are verified by the name, revision and
     * namespace.
     */
    @Test
    public void getModulesFromMountPointTest() {
        final NormalizedNodeContext nodeContext = modulesService.getModule("", null);

        assertNotNull("Node context cannot be null", nodeContext);

        final Collection<MapEntryNode> modules = (Collection<MapEntryNode>) ((ContainerNode) nodeContext .getData())
                .getValue().iterator().next().getValue();

        // check if expected modules were loaded, use module name as map key
        final Map<String, String> loadedModules = new HashMap<>();
        for (MapEntryNode node : modules) {
            final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet()
                    .iterator();
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

            loadedModules.put(name, revision);
        }

        verifyModules(schemaContextBehindMountPoint.getModules(), loadedModules);
    }

    /**
     * Test getting the specific module supported by the server. Module name, revision, namespace and features are
     * compared to have expected values.
     */
    @Test
    public void getModuleTest() throws Exception {
        // get test module
        final NormalizedNodeContext nodeContext = modulesService.getModule(TEST_MODULE, null);
        assertNotNull("Node context cannot be null", nodeContext);

        // verify loaded module
        final MapEntryNode node = ((MapNode) nodeContext.getData()).getValue().iterator().next();
        final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet().iterator();
        verifyLoadedModule("module1", "2014-01-01", "module:1", Collections.emptySet(), mapEntries);
    }

    /**
     * Test getting the specific module supported by the mount point.
     */
    @Test
    public void getModuleFromMountPointTest() {
        // get test moduel schemaContextBehindMountPoint mount point
        final NormalizedNodeContext nodeContext = modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
        assertNotNull("Node context cannot be null", nodeContext);

        // verify loaded module
        final MapEntryNode node = ((MapNode) nodeContext.getData()).getValue().iterator().next();
        final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet().iterator();
        verifyLoadedModule("module1-behind-mount-point", "2014-02-03", "module:1:behind:mount:point",
                Collections.emptySet(), mapEntries);
    }

    /**
     * Test getting all modules supported by the server if Restconf modules does not exist. Test is expected to fail
     * with <code>NullPointerException</code>.
     */
    @Test
    public void getModulesWithoutRestconfModuleNegativeTest() {
        thrown.expect(NullPointerException.class);
        modulesService.getModules(null);
    }

    /**
     * Test getting all modules supported by the mount point if Restconf modules does not exist. Test is expected to
     * fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModulesWithoutRestconfModuleMountPointNegativeTest() {
        thrown.expect(NullPointerException.class);
        modulesService.getModules(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Test getting all modules supported by the mount point with <code>null</code> value of
     * identifier for mount point. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModulesFromMountPointWithNullIdentifier() {
        thrown.expect(NullPointerException.class);
        modulesService.getModules(null, null);
    }

    /**
     * Test getting all modules supported by the server when restconf module contains node with name 'modules' but it
     * is not of type {@link ContainerSchemaNode}. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModulesRestconfModuleWithoutContSchemaNode() {
        thrown.expect(IllegalStateException.class);
        modulesService.getModules(null);
    }

    /**
     * Test getting all modules supported by the mount point if identifier does
     * not contains {@link RestconfConstants#MOUNT}. Catching
     * {@link RestconfDocumentedException} and testing {@link ErrorType}, {@link ErrorTag} and error status code.
     */
    @Test
    public void getModulesFromMountPointWithoutMountIdentifierOnEndOfIdentifier() {
        try {
            modulesService.getModules(TEST_MODULE, null);
            fail("Test should fail due to missing MOUNT constant in identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test getting all modules supported by the mount point when Restconf module contains node with name 'modules' but
     * it is not of type {@link ContainerSchemaNode}. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModulesFromMountPointRestconfModuleWithoutContSchemaNode() {
        thrown.expect(IllegalStateException.class);
        modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Test of getting specific module supported by the server/mount point with <code>null</code> identifier. Test is
     * expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModuleWithNullIdentifierNegativeTest() {
        thrown.expect(NullPointerException.class);
        modulesService.getModule(null, null);
    }

    /**
     * Testing getting specific module supported by the server with module which
     * does not exist in schema. Catching {@link RestconfDocumentedException}
     * and testing {@link ErrorType} and {@link ErrorTag}.
     */
    @Test
    public void getModuleModuleDoesNotExistNegativeTest() {
        try {
            modulesService.getModule(NOT_EXISTING_MODULE, null);
            fail("Test should fail due to searching for not-existing module");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.UNKNOWN_ELEMENT, e.getErrors().get(0).getErrorTag());
            assertEquals("Error code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Testing getting specific module supported by the mount point with module which
     * does not exist in schema. Catching {@link RestconfDocumentedException}
     * and testing {@link ErrorType} and {@link ErrorTag}.
     */
    @Test
    public void getModuleModuleDoesNotExistMountPointNegativeTest() {
        try {
            modulesService.getModule(NOT_EXISTING_MODULE_BEHIND_MOUNT_POINT, null);
            fail("Test should fail due to searching for not-existing module");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.UNKNOWN_ELEMENT, e.getErrors().get(0).getErrorTag());
            assertEquals("Error code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test getting specific module supported by the server when Restconf module contains node with name 'module' but it
     * is not of type {@link ListSchemaNode}. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModuleRestconfModuleWithoutListSchemaNodeNegativeTest() {
        thrown.expect(IllegalStateException.class);
        modulesService.getModule(TEST_MODULE, null);
    }

    /**
     * Test getting specific module supported by the mount point when Restconf module contains node with name 'module'
     * but it is not of type {@link ListSchemaNode}. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModuleRestconfModuleWithoutListSchemaNodeMountPointNegativeTest() {
        thrown.expect(IllegalStateException.class);
        modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Negative test when <code>DOMMountPointServiceHandler</code> contain <code>null</code> a sa reference to
     * <code>DOMMountPointService</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void missingMountPointServiceNegativeTest() {}

    /**
     * Verify if correct modules were loaded into Restconf module by comparison with modules from <code>SchemaContext</code>
     * @param expectedModules Modules from <code>SchemaContext</code>
     * @param loadedModules Loaded modules into Restconf module
     */
    private void verifyModules(final Set<Module> expectedModules, final Map<String, String> loadedModules) {
        assertEquals("Number of loaded modules is not as expected", expectedModules.size(), loadedModules.size());
        for (final Module m : expectedModules) {
            final String name = m.getName();

            final String revision = loadedModules.get(name);
            assertNotNull("Expected module not found", revision);
            assertEquals("Not correct revision of loaded module",
                    SimpleDateFormatUtil.getRevisionFormat().format(m.getRevision()), revision);

            loadedModules.remove(name);
        }
    }

    private void verifyLoadedModule(final String expectedName, final String expectedRevision,
                                    final String expectedNamespace, final Set<Object> expectedFeatures,
                                    final Iterator loadedModuleEntries) {
        while (loadedModuleEntries.hasNext()) {
            final Map.Entry e = ((AbstractMap.SimpleImmutableEntry) loadedModuleEntries.next());
            final String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

            assertTrue("Not allowed keyword", allowedKeywords.contains(key));

            switch (key) {
                case "name":
                    assertEquals("Not correct module was found",
                            expectedName, ((LeafNode) e.getValue()).getValue());
                    break;
                case "revision":
                    assertEquals("Not correct module was found",
                            expectedRevision, ((LeafNode) e.getValue()).getValue());
                    break;
                case "namespace":
                    assertEquals("Not correct module was found",
                            expectedNamespace, ((LeafNode) e.getValue()).getValue());
                    break;
                case "feature":
                    assertEquals("Not correct module was found",
                            expectedFeatures, ((LeafSetNode) e.getValue()).getValue());
                    break;
            }
        }
    }
}
