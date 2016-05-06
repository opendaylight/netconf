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
import static org.mockito.Mockito.mock;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.NOT_EXISTING_MODULE;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.NOT_EXISTING_MODULE_BEHIND_MOUNT_POINT;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.TEST_MODULE;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.TEST_MODULE_BEHIND_MOUNT_POINT;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.getExpectedModules;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.getExpectedModulesBehindMountPoint;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.setupCustomRestconfModule;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.setupCustomRestconfModuleMountPoint;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.setupMissingRestconfModule;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.setupMissingRestconfModuleMountPoint;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.setupNormal;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.setupNormalMountPoint;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.verifyLoadedModule;
import static org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceTestUtils.verifyModules;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Draft11.RestconfModule;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * Unit tests of the {@link RestconfModulesService}
 *
 */
public class RestconfModulesServiceTest {
    @Rule public ExpectedException thrown = ExpectedException.none();

    /**
     * Test non-null init of {@link RestconfModulesServiceImpl}.
     */
    @Test
    public void restconfModulesServiceImplTest() {
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
        final Set<RestconfModulesServiceTestUtils.TestModule> loadedModules = new HashSet<>();

        for (final Object node : modules) {
            final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet()
                    .iterator();
            final RestconfModulesServiceTestUtils.TestModule loadedModule = new RestconfModulesServiceTestUtils
                    .TestModule();

            while (mapEntries.hasNext()) {
                final Map.Entry e = ((AbstractMap.SimpleImmutableEntry) mapEntries.next());
                final String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                switch (key) {
                    case "name":
                        loadedModule.setName((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case "revision":
                        loadedModule.setRevision((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case "namespace":
                        loadedModule.setNamespace((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case "feature":
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
    public void getModulesFromMountPointTest() throws Exception {
        // load testing modules and correct Restconf module behind mount point
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // make test
        final NormalizedNodeContext nodeContext = modulesService.getModules(
                "/mount-point-1:cont/" + RestconfConstants.MOUNT, null);

        // check if expected modules were loaded, use module name as map key
        assertNotNull("Node context cannot be null", nodeContext);
        final Collection<?> modules = (Collection<?>) ((ContainerNode) nodeContext .getData())
                .getValue().iterator().next().getValue();
        final Set<RestconfModulesServiceTestUtils.TestModule> loadedModules = new HashSet<>();

        for (final Object node : modules) {
            final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet()
                    .iterator();
            final RestconfModulesServiceTestUtils.TestModule loadedModule = new RestconfModulesServiceTestUtils
                    .TestModule();

            while (mapEntries.hasNext()) {
                final Map.Entry e = ((AbstractMap.SimpleImmutableEntry) mapEntries.next());
                final String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                switch (key) {
                    case "name":
                        loadedModule.setName((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case "revision":
                        loadedModule.setRevision((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case "namespace":
                        loadedModule.setNamespace((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case "feature":
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
        verifyLoadedModule("module1", "2014-01-01", "module:1", Collections.emptySet(), mapEntries);
    }

    /**
     * Test getting the specific module supported by the mount point.
     */
    @Test
    public void getModuleFromMountPointTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // get test module schemaContextBehindMountPoint mount point
        final NormalizedNodeContext nodeContext = modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);

        // verify loaded module
        assertNotNull("Node context cannot be null", nodeContext);
        final MapEntryNode node = ((MapNode) nodeContext.getData()).getValue().iterator().next();
        final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) node).getChildren().entrySet().iterator();
        verifyLoadedModule("module1-behind-mount-point", "2014-02-03", "module:1:behind:mount:point",
                Collections.emptySet(), mapEntries);
    }

    /**
     * Test getting all modules supported by the server if Restconf module is <code>null</code>. Test is expected to
     * fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModulesWithoutRestconfModuleNegativeTest() {
        // prepare condition
        final RestconfModulesService modulesService = setupMissingRestconfModule();

        // make test
        thrown.expect(NullPointerException.class);
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
        thrown.expect(NullPointerException.class);
        modulesService.getModules(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Test getting all modules supported by the mount point with <code>null</code> value of
     * identifier for mount point. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModulesFromMountPointWithNullIdentifier() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // make test
        thrown.expect(NullPointerException.class);
        modulesService.getModules(null, null);
    }

    /**
     * Test getting all modules supported by the server when restconf module contains node with name
     * {@link RestconfModule#MODULES_CONTAINER_SCHEMA_NODE} but it is not of type {@link ContainerSchemaNode}. Test is
     * expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModulesRestconfModuleWithoutContSchemaNode() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModule(
                "restconf-module-with-illegal-container-modules");

        // make test
        thrown.expect(IllegalStateException.class);
        modulesService.getModules(null);
    }

    /**
     * Test getting all modules supported by the mount point if identifier does
     * not contains {@link RestconfConstants#MOUNT}. Catching
     * {@link RestconfDocumentedException} and testing {@link ErrorType}, {@link ErrorTag} and error status code.
     */
    @Test
    public void getModulesFromMountPointWithoutMountIdentifierOnEndOfIdentifier() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormalMountPoint();

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
     * Test getting all modules supported by the mount point when Restconf module contains node with name
     * {@link RestconfModule#MODULES_CONTAINER_SCHEMA_NODE} but it is not of type {@link ContainerSchemaNode}. Test
     * is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModulesFromMountPointRestconfModuleWithoutContSchemaNode() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModule(
                "restconf-module-with-illegal-container-modules");

        // make test
        thrown.expect(IllegalStateException.class);
        modulesService.getModules(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Test of getting specific module supported by the server/mount point with <code>null</code> identifier. Test is
     * expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getModuleWithNullIdentifierNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupNormal();

        // make test
        thrown.expect(NullPointerException.class);
        modulesService.getModule(null, null);
    }

    /**
     * Testing getting specific module supported by the server with module which
     * does not exist in schema. Catching {@link RestconfDocumentedException}
     * and testing {@link ErrorType} and {@link ErrorTag}.
     */
    @Test
    public void getModuleModuleDoesNotExistNegativeTest() throws Exception {
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
     * Testing getting specific module supported by the mount point with module which
     * does not exist in schema. Catching {@link RestconfDocumentedException}
     * and testing {@link ErrorType} and {@link ErrorTag}.
     */
    @Test
    public void getModuleModuleDoesNotExistMountPointNegativeTest() throws Exception {
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
     * Test getting specific module supported by the server when Restconf module contains node with name
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} but it is not of type {@link ListSchemaNode}. Test is expected
     * to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModuleRestconfModuleWithoutListSchemaNodeNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService =
                setupCustomRestconfModuleMountPoint("restconf-module-with-illegal-list-module");

        // make test
        thrown.expect(IllegalStateException.class);
        modulesService.getModule(TEST_MODULE, null);
    }

    /**
     * Test getting specific module supported by the mount point when Restconf module contains node with name
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} but it is not of type {@link ListSchemaNode}. Test is expected
     * to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getModuleRestconfModuleWithoutListSchemaNodeMountPointNegativeTest() throws Exception {
        // prepare condition
        final RestconfModulesService modulesService = setupCustomRestconfModuleMountPoint(
                "restconf-module-with-illegal-list-module");

        // amke test
        thrown.expect(IllegalStateException.class);
        modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }

    /**
     * Negative test when <code>DOMMountPointServiceHandler</code> contains <code>null</code> reference to
     * <code>DOMMountPointService</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test //fixme
    public void missingMountPointServiceNegativeTest() throws Exception {
        // prepare conditions
        final RestconfModulesService modulesService = setupNormalMountPoint();

        // make test
        thrown.expect(NullPointerException.class);
        modulesService.getModule(TEST_MODULE_BEHIND_MOUNT_POINT, null);
    }
}
