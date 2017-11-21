/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.services.simple.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableClassToInstanceMap;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.broker.impl.mount.DOMMountPointServiceImpl;
import org.opendaylight.controller.md.sal.dom.broker.spi.mount.SimpleDOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.RestconfSchemaService;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@code RestconfSchemaService}.
 */
public class RestconfSchemaServiceTest {
    private static final String MOUNT_POINT = "mount-point-1:cont" + "/" + RestconfConstants.MOUNT + "/";
    private static final String NULL_MOUNT_POINT = "mount-point-2:cont" + "/" + RestconfConstants.MOUNT + "/";
    private static final String NOT_EXISTING_MOUNT_POINT = "mount-point-3:cont" + "/" + RestconfConstants.MOUNT + "/";

    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String TEST_MODULE_BEHIND_MOUNT_POINT = "module1-behind-mount-point/2014-02-03";
    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";

    @Rule public ExpectedException thrown = ExpectedException.none();

    // service under test
    private RestconfSchemaService schemaService;

    // handlers
    @Mock
    private SchemaContextHandler mockContextHandler;
    @Mock
    private DOMMountPointServiceHandler mockMountPointHandler;
    @Mock
    private DOMYangTextSourceProvider sourceProvider;

    // schema context with modules
    private SchemaContext schemaContext;
    // schema context with modules behind mount point
    private SchemaContext schemaContextBehindMountPoint;
    // schema context with mount points
    private SchemaContext schemaContextWithMountPoints;

    // mount point with schema context with modules behind mount point
    private DOMMountPoint mountPoint;
    // mount point with null schema context
    private DOMMountPoint mountPointWithNullSchemaContext;
    // mount point service
    private DOMMountPointService mountPointService;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        this.schemaContext = YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles("/modules"));
        this.schemaContextBehindMountPoint = YangParserTestUtils
                .parseYangSources(TestRestconfUtils.loadFiles("/modules/modules-behind-mount-point"));
        this.schemaContextWithMountPoints =
                YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles("/modules/mount-points"));

        // create and register mount points
        this.mountPoint = SimpleDOMMountPoint.create(
                YangInstanceIdentifier.of(QName.create("mount:point:1", "2016-01-01", "cont")),
                ImmutableClassToInstanceMap.copyOf(new HashMap<>()),
                this.schemaContextBehindMountPoint
        );

        this.mountPointWithNullSchemaContext = SimpleDOMMountPoint.create(
                YangInstanceIdentifier.of(QName.create("mount:point:2", "2016-01-01", "cont")),
                ImmutableClassToInstanceMap.copyOf(new HashMap<>()),
                null
        );

        this.mountPointService = new DOMMountPointServiceImpl();
        ((DOMMountPointServiceImpl) this.mountPointService).registerMountPoint(this.mountPoint);
        ((DOMMountPointServiceImpl) this.mountPointService).registerMountPoint(this.mountPointWithNullSchemaContext);
        when(this.mockMountPointHandler.get()).thenReturn(this.mountPointService);

        this.schemaService = new RestconfSchemaServiceImpl(this.mockContextHandler, this.mockMountPointHandler,
                sourceProvider);
    }

    /**
     * Test if service was successfully created.
     */
    @Test
    public void schemaServiceImplInitTest() {
        assertNotNull("Schema service should be initialized and not null", this.schemaService);
    }

    /**
     * Get schema with identifier of existing module and check if correct module was found.
     */
    @Test
    public void getSchemaTest() {
        // prepare conditions - return not-mount point schema context
        when(this.mockContextHandler.get()).thenReturn(this.schemaContext);

        // make test
        final SchemaExportContext exportContext = this.schemaService.getSchema(TEST_MODULE);

        // verify
        assertNotNull("Export context should not be null", exportContext);

        final Module module = exportContext.getModule();
        assertNotNull("Existing module should be found", module);

        assertEquals("Not expected module name", "module1", module.getName());
        assertEquals("Not expected module revision", "2014-01-01",
                SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("Not expected module namespace", "module:1", module.getNamespace().toString());
    }

    /**
     * Get schema with identifier of not-existing module. <code>SchemaExportContext</code> is still created, but module
     * should be set to <code>null</code>.
     */
    @Test
    public void getSchemaForNotExistingModuleTest() {
        // prepare conditions - return not-mount point schema context
        when(this.mockContextHandler.get()).thenReturn(this.schemaContext);

        // make test
        final SchemaExportContext exportContext = this.schemaService.getSchema(NOT_EXISTING_MODULE);

        // verify
        assertNotNull("Export context should not be null", exportContext);
        assertNull("Not-existing module should not be found", exportContext.getModule());
    }

    /**
     * Get schema with identifier of existing module behind mount point and check if correct module was found.
     */
    @Test
    public void getSchemaMountPointTest() {
        // prepare conditions - return schema context with mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContextWithMountPoints);

        // make test
        final SchemaExportContext exportContext =
                this.schemaService.getSchema(MOUNT_POINT + TEST_MODULE_BEHIND_MOUNT_POINT);

        // verify
        assertNotNull("Export context should not be null", exportContext);

        final Module module = exportContext.getModule();
        assertNotNull("Existing module should be found", module);

        assertEquals("Not expected module name", "module1-behind-mount-point", module.getName());
        assertEquals("Not expected module revision", "2014-02-03",
                SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("Not expected module namespace", "module:1:behind:mount:point", module.getNamespace().toString());
    }

    /**
     * Get schema with identifier of not-existing module behind mount point. <code>SchemaExportContext</code> is still
     * created, but module should be set to <code>null</code>.
     */
    @Test
    public void getSchemaForNotExistingModuleMountPointTest() {
        // prepare conditions - return schema context with mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContextWithMountPoints);

        // make test
        final SchemaExportContext exportContext = this.schemaService.getSchema(MOUNT_POINT + NOT_EXISTING_MODULE);

        // verify
        assertNotNull("Export context should not be null", exportContext);
        assertNull("Not-existing module should not be found", exportContext.getModule());
    }

    /**
     * Try to get schema with <code>null</code> <code>SchemaContext</code> expecting <code>NullPointerException</code>.
     */
    @Test
    public void getSchemaWithNullSchemaContextTest() {
        // prepare conditions - returned schema context is null
        when(this.mockContextHandler.get()).thenReturn(null);

        // make test
        this.thrown.expect(NullPointerException.class);
        this.schemaService.getSchema(TEST_MODULE);
    }

    /**
     * Try to get schema with <code>null</code> <code>SchemaContext</code> for mount points.
     * <code>NullPointerException</code> is expected.
     */
    @Test
    public void getSchemaWithNullSchemaContextMountPointTest() {
        // prepare conditions - returned schema context for mount points is null
        when(this.mockContextHandler.get()).thenReturn(null);

        // make test
        this.thrown.expect(NullPointerException.class);
        this.schemaService.getSchema(MOUNT_POINT + TEST_MODULE_BEHIND_MOUNT_POINT);
    }

    /**
     * Try to get schema with <code>null</code> <code>SchemaContext</code> behind mount point when using
     * <code>NULL_MOUNT_POINT</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getSchemaNullSchemaContextBehindMountPointTest() {
        // prepare conditions - return correct schema context for mount points (this is not null)
        when(this.mockContextHandler.get()).thenReturn(this.schemaContextWithMountPoints);

        // make test - call service on mount point with null schema context
        this.thrown.expect(NullPointerException.class);
        // NULL_MOUNT_POINT contains null schema context
        this.schemaService.getSchema(NULL_MOUNT_POINT + TEST_MODULE_BEHIND_MOUNT_POINT);
    }

    /**
     * Try to get schema with null identifier expecting <code>NullPointerException</code>. The same processing is for
     * server and also for mount point.
     */
    @Test
    public void getSchemaWithNullIdentifierTest() {
        // prepare conditions - return correct schema context
        when(this.mockContextHandler.get()).thenReturn(this.schemaContext);

        // make test
        this.thrown.expect(NullPointerException.class);
        this.schemaService.getSchema(null);
    }

    /**
     * Try to get schema with empty (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void getSchemaWithEmptyIdentifierTest() {
        // prepare conditions - return correct schema context
        when(this.mockContextHandler.get()).thenReturn(this.schemaContext);

        // make test and verify
        try {
            this.schemaService.getSchema("");
            fail("Test should fail due to invalid identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema with empty (not valid) identifier behind mount point catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithEmptyIdentifierMountPointTest() {
        // prepare conditions - return correct schema context with mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContextWithMountPoints);

        // make test and verify
        try {
            this.schemaService.getSchema(MOUNT_POINT + "");
            fail("Test should fail due to invalid identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema with not-parsable identifier catching <code>RestconfDocumentedException</code>. Error type,
     * error tag and error status code are compared to expected values.
     */
    @Test
    public void getSchemaWithNotParsableIdentifierTest() {
        // prepare conditions - return correct schema context without mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContext);

        // make test and verify
        try {
            this.schemaService.getSchema("01_module/2016-01-01");
            fail("Test should fail due to invalid identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema behind mount point with not-parsable identifier catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithNotParsableIdentifierMountPointTest() {
        // prepare conditions - return correct schema context with mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContextWithMountPoints);

        // make test and verify
        try {
            this.schemaService.getSchema(MOUNT_POINT + "01_module/2016-01-01");
            fail("Test should fail due to invalid identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema with wrong (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     *
     * <p>
     * Not valid identifier contains only revision without module name.
     */
    @Test
    public void getSchemaWrongIdentifierTest() {
        // prepare conditions - return correct schema context without mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContext);

        // make test and verify
        try {
            this.schemaService.getSchema("2014-01-01");
            fail("Test should fail due to invalid identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema with wrong (not valid) identifier behind mount point catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     *
     * <p>
     * Not valid identifier contains only revision without module name.
     */
    @Test
    public void getSchemaWrongIdentifierMountPointTest() {
        // prepare conditions - return correct schema context with mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContextWithMountPoints);

        // make test and verify
        try {
            this.schemaService.getSchema(MOUNT_POINT + "2014-01-01");
            fail("Test should fail due to invalid identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema with identifier which does not contain revision catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithoutRevisionTest() {
        // prepare conditions - return correct schema context without mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContext);

        // make test and verify
        try {
            this.schemaService.getSchema("module");
            fail("Test should fail due to invalid identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get schema behind mount point with identifier when does not contain revision catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithoutRevisionMountPointTest() {
        // prepare conditions - return correct schema context with mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContextWithMountPoints);

        // make test and verify
        try {
            this.schemaService.getSchema(MOUNT_POINT + "module");
            fail("Test should fail due to invalid identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test when mount point module is not found in current <code>SchemaContext</code> for mount points.
     * <code>IllegalArgumentException</code> exception is expected.
     */
    @Test
    public void getSchemaContextWithNotExistingMountPointTest() {
        // prepare conditions - return schema context with mount points
        when(this.mockContextHandler.get()).thenReturn(this.schemaContextWithMountPoints);

        // make test
        this.thrown.expect(IllegalArgumentException.class);
        this.schemaService.getSchema(NOT_EXISTING_MOUNT_POINT + TEST_MODULE_BEHIND_MOUNT_POINT);
    }
}
