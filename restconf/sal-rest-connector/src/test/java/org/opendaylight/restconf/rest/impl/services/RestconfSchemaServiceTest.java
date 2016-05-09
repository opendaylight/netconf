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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableClassToInstanceMap;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.broker.impl.mount.DOMMountPointServiceImpl;
import org.opendaylight.controller.md.sal.dom.broker.spi.mount.SimpleDOMMountPoint;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.schema.RestconfSchemaService;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@code RestconfSchemaService}
 */
public class RestconfSchemaServiceTest {
    private static final String MOUNT_POINT = "mount-point-1:cont" + "/" + RestconfConstants.MOUNT + "/";

    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String TEST_MODULE_BEHIND_MOUNT_POINT = MOUNT_POINT + "module1-behind-mount-point/2014-02-03";

    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";
    private static final String NOT_EXISTING_MODULE_BEHIND_MOUNT_POINT = MOUNT_POINT + NOT_EXISTING_MODULE;

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Mock private SchemaContextHandler contextHandler;
    @Mock private DOMMountPointServiceHandler mountPointHandler;

    // service under test
    private RestconfSchemaService schemaService;

    private SchemaContext schemaContext;
    private SchemaContext schemaContextBehindMountPoint;
    private SchemaContext schemaContextWithMountPoints;

    private DOMMountPointService mountPointService;
    private DOMMountPoint mountPoint;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        schemaContextBehindMountPoint = TestRestconfUtils.loadSchemaContext("/modules/modules-behind-mount-point");
        schemaContextWithMountPoints = TestRestconfUtils.loadSchemaContext("/modules/mount-points");

        // create and register mount point
        mountPoint = SimpleDOMMountPoint.create(
                YangInstanceIdentifier.EMPTY,
                ImmutableClassToInstanceMap.copyOf(new HashMap<>()),
                schemaContextBehindMountPoint
        );

        mountPointService = new DOMMountPointServiceImpl();
        ((DOMMountPointServiceImpl) mountPointService).registerMountPoint(mountPoint);

        when(mountPointHandler.getDOMMountPointService()).thenReturn(mountPointService);

        schemaService = new RestconfSchemaServiceImpl(contextHandler, mountPointHandler);
    }

    /**
     * Test if service was successfully created
     */
    @Test
    public void schemaServiceImplInitTest() {
        assertNotNull("Service should be initalizied and not null", schemaService);
    }

    /**
     * Get schema and find existing module and check if correct module was found.
     */
    @Test
    public void getSchemaTest() {
        // prepare conditions
        when(contextHandler.getSchemaContext()).thenReturn(schemaContext);

        // make test
        final SchemaExportContext exportContext = schemaService.getSchema(TEST_MODULE);
        assertNotNull("Export context should not be null", exportContext);

        final Module module = exportContext.getModule();
        assertNotNull("Existing module should be found", module);

        assertEquals("Not expected module name", "module1", module.getName());
        assertEquals("Not expected module revision", "2014-01-01",
                SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("Not expected module namespace", "module:1", module.getNamespace().toString());
    }

    /**
     * Get schema with not-existing module. <code>SchemaExportContext</code> is still created, but module should be
     * set to <code>null</code>.
     */
    @Test
    public void getSchemaForNotExistingModuleTest() {
        // prepare conditions
        when(contextHandler.getSchemaContext()).thenReturn(schemaContext);

        // make test
        final SchemaExportContext exportContext = schemaService.getSchema(NOT_EXISTING_MODULE);

        assertNotNull("Export context should not be null", exportContext);
        assertNull("Not-existing module should not be found", exportContext.getModule());
    }

    /**
     * Get schema for existing module behind mount point and check if correct module was found.
     */
    @Test
    public void getSchemaMountPointTest() {
        when(contextHandler.getSchemaContext()).thenReturn(schemaContextWithMountPoints);

        final SchemaExportContext exportContext = schemaService.getSchema(TEST_MODULE_BEHIND_MOUNT_POINT);
        assertNotNull("Export context should not be null", exportContext);

        final Module module = exportContext.getModule();
        assertNotNull("Existing module should be found", module);

        assertEquals("Not expected module name", "module1-behind-mount-point", module.getName());
        assertEquals("Not expected module revision", "2014-02-03",
                SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("Not expected module namespace", "module:1:behind:mount:point", module.getNamespace().toString());
    }

    /**
     * Get schema for not-existing module behind mount point. <code>SchemaExportContext</code> is still created, but
     * module should be set to <code>null</code>.
     */
    @Test
    public void getSchemaForNotExistingModuleMountPointTest() {
        // prepare conditions
        when(mountPoint.getSchemaContext()).thenReturn(schemaContextBehindMountPoint);

        // make test
        final SchemaExportContext exportContext = schemaService.getSchema(NOT_EXISTING_MODULE_BEHIND_MOUNT_POINT);

        assertNotNull("Export context should not be null", exportContext);
        assertNull("Not-existing module should not be found", exportContext.getModule());
    }

    // NEGATIVE TESTS

    /**
     * Try to get schema with null <code>SchemaContext</code> catching <code>NullPointerException</code>.
     */
    @Test(expected = NullPointerException.class)
    public void getSchemaWithNullSchemaContextTest() {
        // prepare conditions - returned schema context is null
        when(contextHandler.getSchemaContext()).thenReturn(null);

        // make test
        thrown.expect(NullPointerException.class);
        schemaService.getSchema(TEST_MODULE);
    }

    /**
     * Try to get schema with null <code>SchemaContext</code> behind mount point. <code>NullPointerException</code>
     * is expected.
     */
    @Test(expected = NullPointerException.class)
    public void getSchemaWithNullSchemaContextMountPointTest() {
        // prepare conditions - returned schema context is null
        when(contextHandler.getSchemaContext()).thenReturn(null);

        // make test
        thrown.expect(NullPointerException.class);
        schemaService.getSchema(TEST_MODULE_BEHIND_MOUNT_POINT);
    }

    /**
     * Try to get schema with null identifier catching <code>NullPointerException</code>.
     */
    @Test(expected = NullPointerException.class)
    public void getSchemaWithNullIdentifierTest() {
        thrown.expect(NullPointerException.class);
        schemaService.getSchema(null);
    }

    /**
     * Try to get schema with null identifier behind mount point catching <code>NullPointerException</code>.
     */
    @Test(expected = NullPointerException.class)
    public void getSchemaWithNullIdentifierMountPointsTest() {
        thrown.expect(NullPointerException.class);
        schemaService.getSchema(null);
    }

    /**
     * Try to get schema with empty (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void getSchemaWithEmptyIdentifierTest() {
        try {
            schemaService.getSchema("");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema with not-parsable identifier catching <code>RestconfDocumentedException</code>. Error type,
     * error tag and error status code are compared to expected values.
     * Not parsable means not following the rule: (ALPHA / "_")*(ALPHA / DIGIT / "_" / "-" / ".")
     */
    @Test
    public void getSchemaWithNotParsableIdentifier() {
        try {
            schemaService.getSchema("01_module");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema with wrong (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     * Not valid identifier contains only revision without module name.
     */
    @Test
    public void getSchemaWrongIdentifierTest() {
        try {
            schemaService.getSchema("2014-01-01");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema with wrong (not valid) identifier behind mount point catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     * <p>
     * Not valid identifier contains only revision without module name.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWrongIdentifierMountPointTest() {}

    /**
     * Try to get schema with identifier which does not contain revision catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWithoutRevisonTest() {}

    /***
     * Try to get schema behind mount point with identifier when does not contain revision catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void getSchemaWithoutRevisonMountPointTest() {}

    /*
     * Try to get schema when it is not possible to create <code>YangInstanceIdentifier</code> for specified mount
     * point. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getSchemaForNotExistingMountPoint() {}

    /**
     * Negative test when mount point module is not found in current <code>SchemaContext</code>.
     * <code>NullPointerException</code> exception is expected.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void getSchemaContextWithNotExistingMountPointTest() {}
}
