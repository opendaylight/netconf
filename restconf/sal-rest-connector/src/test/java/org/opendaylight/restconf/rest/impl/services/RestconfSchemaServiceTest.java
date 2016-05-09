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
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.schema.RestconfSchemaService;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfSchemaServiceTest {
    private static final String MOUNT_POINT = "mount:point" + "/" + RestconfConstants.MOUNT + "/";

    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String TEST_MODULE_BEHIND_MOUNT_POINT = MOUNT_POINT + TEST_MODULE;

    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";
    private static final String NOT_EXISTING_MODULE_BEHIND_MOUNT_POINT = MOUNT_POINT + NOT_EXISTING_MODULE;

    @Mock
    private SchemaContextHandler contextHandler;
    @Mock
    private DOMMountPointServiceHandler mountPointHandler;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;

    private RestconfSchemaService schemaService;
    private SchemaContext schemaContext;
    // FIXME
    private SchemaContext schemaContextBehindMountPoint;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        schemaContextBehindMountPoint = TestRestconfUtils.loadSchemaContext("/modules/modules-behind-mount-point");

        when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
        when(mountPointHandler.getDOMMountPointService()).thenReturn(mountPointService);
        when(mountPointService.getMountPoint(any(YangInstanceIdentifier.class))).thenReturn(Optional.of(mountPoint));
        when(mountPoint.getSchemaContext()).thenReturn(schemaContextBehindMountPoint);

        schemaService = new RestconfSchemaServiceImpl(contextHandler, mountPointHandler);
    }

    /**
     * Test if service was successfully created
     */
    @Test
    public void schemaServiceImplInitTest() {
        assertNotNull(schemaService);
    }

    /**
     * Get schema and find existing module and check if correct module was found.
     */
    @Test
    public void getSchemaTest() {
        final SchemaExportContext exportContext = schemaService.getSchema(TEST_MODULE);
        assertNotNull(exportContext);

        final Module module = exportContext.getModule();
        assertNotNull("Existing module should be found", module);

        assertEquals("module1", module.getName());
        assertEquals("2014-01-01", SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("module:1", module.getNamespace().toString());
    }

    /**
     * Get schema with not-existing module. <code>SchemaExportContext</code> is still created, but module should be
     * set to <code>null</code>.
     */
    @Test
    public void getSchemaForNotExistinModuleTest() {
        final SchemaExportContext exportContext = schemaService.getSchema(NOT_EXISTING_MODULE);

        assertNotNull("Export context should not be null", exportContext);
        assertNull("Not-existing module should not be found", exportContext.getModule());
    }

    // NEW TEST PLAN

    /**
     * Get schema for existing module behind mount point and check if correct module was found.
     */
    @Ignore
    @Test
    public void getSchemaMountPointTest() {}

    /**
     * Get schema for not-existing module behind mount point. <code>SchemaExportContext</code> is still created, but
     * module should be set to <code>null</code>.
     */
    @Ignore
    @Test
    public void getSchemaForNotExistingModuleMountPointTest() {}

    // NEGATIVE TESTS

    // null inputs **

    /**
     * Try to get schema with null <code>SchemaContext</code> catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getSchemaWithNullSchemaContextTest() {}

    /**
     * Try to get schema with null <code>SchemaContext</code> behind mount point. <code>NullPointerException</code>
     * is expected.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getSchemaWithNullSchemaContextMountPointTest() {}

    /**
     * Try to get schema with null identifier catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getSchemaWithNullIdentifierTest() {}

    /**
     * Try to get schema with null identifier behind mount point catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getSchemaWithNullIdentifierMountPointsTest() {}

    /**
     * Try to get schema with empty (not valid) identifier catching <code>RestconfDocumentedException</code>.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWithEmptyIdentifierTest() {}

    /**
     * Try to get schema with not-parsable identifier catching <code>RestconfDocumentedException</code>.
     * Not parsable means not following the rule: (ALPHA / "_")*(ALPHA / DIGIT / "_" / "-" / ".")
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWithNotParsableIdentifier() {}

    /**
     * Try to get schema with wrong (not valid) identifier catching <code>RestconfDocumentedException</code>.
     * Not valid identifier contains only revision without module name.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWrongIdentifierTest() {}

    /**
     * Try to get schema with wrong (not valid) identifier behind mount point catching
     * <code>RestconfDocumentedException</code>.
     * Not valid identifier contains only revision without module name.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWrongIdentifierMountPointTest() {}

    /**
     * Try to get schema with identifier which does not contain revision catching
     * <code>RestconfDocumentedException</code>.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWithoutRevisonTest() {}

    /***
     * Try to get schema behind mount point with identifier when does not contain revision catching
     * <code>RestconfDocumentedException</code>.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWithoutRevisonMountPointTest() {}

    /*
     * Try to get schema when it is not possible to create <code>YangInstanceIdentifier</code> for specified mount
     * point.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getSchemaForNotExistingMountPoint() {}

    /**
     * Negative test when mount point modules is not found in current <code>SchemaContext</code>.
     * <code>NullPointerException</code> exception is expected.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getSchemaContextWithNotExistingMountPointTest() {}
}
