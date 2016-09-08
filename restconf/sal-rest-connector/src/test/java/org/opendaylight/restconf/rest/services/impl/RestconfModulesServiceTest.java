/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.ALLOWED_KEYWORDS;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.MOUNT_POINT;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.NOT_EXISTING_MODULE;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.NOT_EXISTING_MODULE_BEHIND_MOUNT_POINT;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.NOT_REGISTERED_MOUNT_POINT;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.TEST_MODULE;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.TEST_MODULE_BEHIND_MOUNT_POINT;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.TEST_MODULE_BEHIND_NOT_REGISTERED_MOUNT_POINT;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.getExpectedModules;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.getExpectedModulesBehindMountPoint;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.setupCustomRestconfModule;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.setupCustomRestconfModuleMountPoint;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.setupMissingRestconfModule;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.setupMissingRestconfModuleMountPoint;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.setupNormal;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.setupNormalMountPoint;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.setupNullMountPointService;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.verifyModule;
import static org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.verifyModules;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Draft16.RestconfModule;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.rest.services.api.RestconfModulesService;
import org.opendaylight.restconf.rest.services.impl.RestconfModulesServiceTestUtils.TestModule;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * Unit tests of the {@link RestconfModulesServiceImpl}
 *
 */
public class RestconfModulesServiceTest {
    @Rule public ExpectedException thrown = ExpectedException.none();

    /**
     * Test non-null init of {@link RestconfModulesServiceImpl}.
     */
    @Test
    public void restconfModulesServiceImplInitTest() {
        assertNotNull("Modules service should be initialized and not null",
                new RestconfModulesServiceImpl(mock(SchemaContextHandler.class),
                        mock(DOMMountPointServiceHandler.class)));
    }

    /**
     * Test getting all modules supported by the server. Retrieved modules are verified by the name, namespace and
     * revision.
     */
    @Test
    public void getModulesTest() throws Exception {
        // load schema context with testing modules and correct Restconf module
        final RestconfModulesService modulesService = setupNormal();

        // make test
        final NormalizedNodeContext nodeContext = modulesService.getModules(null);

        // check if expected modules were loaded
        assertNotNull("Node context cannot be null", nodeContext);
        final Collection<?> modules = (Collection<?>) ((ContainerNode) nodeContext .getData())
                .getValue().iterator().next().getValue();
        final Set<TestModule> loadedModules = new HashSet<>();

        for (final Object node : modules) {
            final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet()
                    .iterator();
            final TestModule loadedModule = new TestModule();

            while (mapEntries.hasNext()) {
                final Entry e = ((SimpleImmutableEntry) mapEntries.next());
                final String key = ((NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                assertTrue("Not allowed keyword", ALLOWED_KEYWORDS.contains(key));

                switch (key) {
                    case RestconfMappingNodeConstants.NAME:
                        loadedModule.setName((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.NAMESPACE:
                        loadedModule.setNamespace((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.REVISION:
                         loadedModule.setRevision((String) ((LeafNode) e.getValue()).getValue());
                    break;
                    case RestconfMappingNodeConstants.FEATURE:
                        break;
                }
            }

            loadedModules.add(loadedModule);
        }

        verifyModules(getExpectedModules(), loadedModules);
    }

    /**
     * Test getting all modules supported by the mount point. Retrieved modules are verified by the name, namespace and
     * revision.
     */
    @Test
    public void getModulesMountPointTest() throws Exception {
        // load testing modules and correct Restconf module behind mount point
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // make test
        final NormalizedNodeContext nodeContext = modulesService.getModules(MOUNT_POINT, null);

        // check if expected modules were loaded, use module name as map key
        assertNotNull("Node context cannot be null", nodeContext);
        final Collection<?> modules = (Collection<?>) ((ContainerNode) nodeContext .getData())
                .getValue().iterator().next().getValue();
        final Set<TestModule> loadedModules = new HashSet<>();

        for (final Object node : modules) {
            final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet()
                    .iterator();
            final TestModule loadedModule = new TestModule();

            while (mapEntries.hasNext()) {
                final Entry e = ((SimpleImmutableEntry) mapEntries.next());
                final String key = ((NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                assertTrue("Not allowed keyword", ALLOWED_KEYWORDS.contains(key));

                switch (key) {
                    case RestconfMappingNodeConstants.NAME:
                        loadedModule.setName((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.NAMESPACE:
                        loadedModule.setNamespace((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.REVISION:
                         loadedModule.setRevision((String) ((LeafNode) e.getValue()).getValue());
                    break;
                    case RestconfMappingNodeConstants.FEATURE:
                        break;
                }
            }

            loadedModules.add(loadedModule);
        }

        verifyModules(getExpectedModulesBehindMountPoint(), loadedModules);
    }

    /**
     * Test getting the specific module supported by the server. Module name, revision, namespace and features are
     * compared to have expected values.
     */
    @Test
    public void getModuleTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormal();

        // get test module
        final NormalizedNodeContext nodeContext = modulesService.getModule(TEST_MODULE, null);

        // verify loaded module
        assertNotNull("Node context cannot be null", nodeContext);
        final MapEntryNode node = ((MapNode) nodeContext.getData()).getValue().iterator().next();
        final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet().iterator();
        verifyModule("module1", "module:1", "2014-01-01", Collections.emptySet(), mapEntries);
    }

    /**
     * Test getting the specific module supported by the mount point.
     */
    @Test
    public void getModuleMountPointTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // get test module schemaContextBehindMountPoint mount point
        final NormalizedNodeContext nodeContext = modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);

        // verify loaded module
        assertNotNull("Node context cannot be null", nodeContext);
        final MapEntryNode node = ((MapNode) nodeContext.getData()).getValue().iterator().next();
        final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet().iterator();
        verifyModule("module1-behind-mount-point", "module:1:behind:mount:point", "2014-02-03",
                Collections.emptySet(), mapEntries);
    }

    /**
     * Test getting all modules supported by the server if Restconf module is <code>null</code>. Test is expected to
     * fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModulesWithoutRestconfModuleNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupMissingRestconfModule();

        // make test
        this.thrown.expect(NullPointerException.class);
        modulesService.getModules(null);
    }

    /**
     * Test getting all modules supported by the mount point if Restconf module is <code>null</code>. Test is expected
     * to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModulesWithoutRestconfModuleMountPointNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupMissingRestconfModuleMountPoint();

        // make test
        this.thrown.expect(NullPointerException.class);
        modulesService.getModules(MOUNT_POINT, null);
    }

    /**
     * Test getting all modules supported by the mount point with <code>null</code> value of
     * identifier for mount point. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModulesWithNullIdentifierOfMountPointNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // make test
        this.thrown.expect(NullPointerException.class);
        modulesService.getModules(null, null);
    }

    /**
     * Test getting all modules supported by the mount point if identifier does
     * not contains {@link RestconfConstants#MOUNT}. Catching
     * {@link RestconfDocumentedException} and testing error tyupe, error tag and error status code.
     */
    @Test
    public void getModulesWithoutMountConstantInMountPointIdentifierNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormalMountPoint();

        try {
            modulesService.getModules(MOUNT_POINT.replace("/" + RestconfConstants.MOUNT + "/", ""), null);
            fail("Test should fail due to missing " + RestconfConstants.MOUNT + " constant in mount point identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test getting all modules supported by the server when Restconf module does not contain node with name
     * {@link RestconfModule#MODULES_CONTAINER_SCHEMA_NODE}. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    public void getModulesRestconfModuleWithMissingContainerModulesNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModule(
                "restconf-module-with-missing-container-modules");

        // make test
        try {
            modulesService.getModules(null);
            fail("Test should fail due to missing " + RestconfModule.MODULES_CONTAINER_SCHEMA_NODE
                    + " node in Restconf module");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test getting all modules supported by the server when Restconf module contains node with name
     * {@link RestconfModule#MODULES_CONTAINER_SCHEMA_NODE} but it is not of type {@link ContainerSchemaNode}. Test is
     * expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModulesRestconfModuleWithIllegalContainerModulesNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModule(
                "restconf-module-with-illegal-container-modules");

        // make test
        this.thrown.expect(IllegalStateException.class);
        modulesService.getModules(null);
    }

    /**
     * Test getting all modules supported by the mount point when Restconf module does not contain node with name
     * {@link RestconfModule#MODULES_CONTAINER_SCHEMA_NODE}. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    public void getModulesRestconfModuleWithMissingContainerModulesMountPointNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModuleMountPoint(
                "restconf-module-with-missing-container-modules");

        try {
            modulesService.getModules(MOUNT_POINT, null);
            fail("Test should fail due to missing " + RestconfModule.MODULES_CONTAINER_SCHEMA_NODE
                    + " node in Restconf module");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test getting all modules supported by the mount point when Restconf module contains node with name
     * {@link RestconfModule#MODULES_CONTAINER_SCHEMA_NODE} but it is not of type {@link ContainerSchemaNode}. Test
     * is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModulesRestconfModuleWithIllegalContainerModulesMountPointNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModuleMountPoint(
                "restconf-module-with-illegal-container-modules");

        // make test
        this.thrown.expect(IllegalStateException.class);
        modulesService.getModules(MOUNT_POINT, null);
    }

    /**
     * Test of getting specific module supported by the server/mount point with <code>null</code> identifier. Test is
     * expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModuleWithNullIdentifierNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = spy(new RestconfModulesServiceImpl(
                mock(SchemaContextHandler.class),
                mock(DOMMountPointServiceHandler.class)));

        // make test
        this.thrown.expect(NullPointerException.class);
        modulesService.getModule(null, null);
    }

    /**
     * Testing getting specific module supported by the server with module identifier which
     * does not exist in <code>SchemaContext</code>. Catching {@link RestconfDocumentedException}
     * and testing error type, error tag and error status code.
     */
    @Test
    public void getModuleNotExistModuleNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormal();

        // make test
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
     * Testing getting specific module supported by the mount point with module identifier which
     * does not exist in <code>SchemaContext</code>. Catching {@link RestconfDocumentedException}
     * and testing error type, error tag and error status code.
     */
    @Test
    public void getModuleNotExistModuleMountPointNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // make test
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
     * Test getting specific module supported by the server when Restconf module is null. Test is expected to fail
     * with <code>NullPointerException</code>.
     */
    @Test
    public void getModuleWithoutRestconfModuleNegativeTest() throws Exception {
        // prepare conditions
        final RestconfModulesService modulesService = setupMissingRestconfModule();

        this.thrown.expect(NullPointerException.class);
        modulesService.getModule(TEST_MODULE, null);
    }

    /**
     * Test getting specific module supported by the mount point when Restconf module is null. Test is expected to fail
     * with <code>NullPointerException</code>.
     */
    @Test
    public void getModuleWithoutRestconfModuleMountPointNegativeTest() throws Exception {
        // prepare conditions
        final RestconfModulesService modulesService = setupMissingRestconfModuleMountPoint();

        this.thrown.expect(NullPointerException.class);
        modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Test getting specific module supported by the server when Restconf module does not contain node with
     * name {@link RestconfModule#MODULE_LIST_SCHEMA_NODE}. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    public void getModuleRestconfModuleWithMissingListModuleNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService =
                setupCustomRestconfModule("restconf-module-with-missing-list-module");

        // make test
        try {
            modulesService.getModule(TEST_MODULE, null);
            fail("Test should fail due to missing " + RestconfModule.MODULE_LIST_SCHEMA_NODE
                    + " node in Restconf module");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error code is not correct", 404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test getting specific module supported by the server when Restconf module contains node with name
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} but it is not of type {@link ListSchemaNode}. Test is expected
     * to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModuleRestconfModuleWitIllegalListSchemaNodeNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModule(
                "restconf-module-with-illegal-list-module");

        // make test
        this.thrown.expect(IllegalStateException.class);
        modulesService.getModule(TEST_MODULE, null);
    }

    /**
     * Test getting specific module supported by the mount point when Restconf module does not contain node with
     * name {@link RestconfModule#MODULE_LIST_SCHEMA_NODE}. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    public void getModuleRestconfModuleWithMissingListModuleMountPointNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService =
                setupCustomRestconfModuleMountPoint("restconf-module-with-missing-list-module");

        // make test
        try {
            modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
            fail("Test should fail due to missing " + RestconfModule.MODULE_LIST_SCHEMA_NODE
                    + " node in Restconf module");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error code is not correct", 404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test getting specific module supported by the mount point when Restconf module contains node with name
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} but it is not of type {@link ListSchemaNode}. Test is expected
     * to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModuleRestconfModuleWitIllegalListModuleMountPointNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModuleMountPoint(
                "restconf-module-with-illegal-list-module");

        // make test
        this.thrown.expect(IllegalStateException.class);
        modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Negative test of specific module supported by the mount point when <code>DOMMountPointServiceHandler</code>
     * contains <code>null</code> reference to  <code>DOMMountPointService</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void getModuleMissingMountPointServiceNegativeTest() throws Exception {
        // prepare conditions
        final RestconfModulesService modulesService = setupNullMountPointService();

        // make test
        this.thrown.expect(NullPointerException.class);
        modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Negative test of getting all modules supported by the mount point when <code>DOMMountPointServiceHandler</code>
     * contains <code>null</code> reference to  <code>DOMMountPointService</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void getModulesMissingMountPointServiceNegativeTest() throws Exception {
        // prepare conditions
        final RestconfModulesService modulesService = setupNullMountPointService();

        // make test
        this.thrown.expect(NullPointerException.class);
        modulesService.getModules(MOUNT_POINT, null);
    }

    /**
     * Negative test of getting specific module supported by the mount point when specified mount point is not found
     * (it is not registered in <code>DOMMountPointService</code>). Test is expected to fail with
     * <code>IllegalStateException</code>.
     */
    @Test
    public void getModuleMountPointNotFoundNegativeTest() throws Exception {
        // prepare conditions
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // make test
        this.thrown.expect(IllegalStateException.class);
        modulesService.getModule(TEST_MODULE_BEHIND_NOT_REGISTERED_MOUNT_POINT, null);
    }

    /**
     * Negative test of getting all modules supported by the mount point when specified mount point is not found (it
     * is not registered in <code>DOMMountPointService</code>). Test is expected to fail with
     * <code>IllegalStateException</code>.
     */
    @Test
    public void getModulesMountPointNotFoundNegativeTest() throws Exception {
        // prepare conditions
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // make test
        this.thrown.expect(IllegalStateException.class);
        modulesService.getModules(NOT_REGISTERED_MOUNT_POINT, null);
    }
}
