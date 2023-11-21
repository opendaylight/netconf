/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableClassToInstanceMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfSchemaService;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@code RestconfSchemaService}.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfSchemaServiceTest {
    private static final String MOUNT_POINT = "mount-point-1:cont/yang-ext:mount/";
    private static final String NULL_MOUNT_POINT = "mount-point-2:cont/yang-ext:mount/";
    private static final String NOT_EXISTING_MOUNT_POINT = "mount-point-3:cont/yang-ext:mount/";

    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String TEST_MODULE_BEHIND_MOUNT_POINT = "module1-behind-mount-point/2014-02-03";
    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";

    // schema context with modules
    private static final EffectiveModelContext SCHEMA_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/modules");
    // schema context with modules behind mount point
    private static final EffectiveModelContext SCHEMA_CONTEXT_BEHIND_MOUNT_POINT =
        YangParserTestUtils.parseYangResourceDirectory("/modules/modules-behind-mount-point");
    // schema context with mount points
    private static final EffectiveModelContext SCHEMA_CONTEXT_WITH_MOUNT_POINTS =
        YangParserTestUtils.parseYangResourceDirectory("/modules/mount-points");

    // service under test
    private RestconfSchemaService schemaService;

    // handlers
    @Mock
    private DOMSchemaService mockSchemaService;
    @Mock
    private DOMYangTextSourceProvider mockSourceProvider;

    @Before
    public void setup() throws Exception {
        final var mountPointService = new DOMMountPointServiceImpl();
        // create and register mount points
        mountPointService
                .createMountPoint(YangInstanceIdentifier.of(QName.create("mount:point:1", "2016-01-01", "cont")))
                .addService(DOMSchemaService.class, FixedDOMSchemaService.of(SCHEMA_CONTEXT_BEHIND_MOUNT_POINT))
                .register();
        mountPointService
                .createMountPoint(YangInstanceIdentifier.of(QName.create("mount:point:2", "2016-01-01", "cont")))
                .register();

        when(mockSchemaService.getExtensions())
            .thenReturn(ImmutableClassToInstanceMap.of(DOMYangTextSourceProvider.class, mockSourceProvider));
        schemaService = new RestconfSchemaServiceImpl(mockSchemaService, mountPointService);
    }

    /**
     * Get schema with identifier of existing module and check if correct module was found.
     */
    @Test
    public void getSchemaTest() {
        // prepare conditions - return not-mount point schema context
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT);

        // make test
        final var exportContext = schemaService.getSchema(TEST_MODULE);

        // verify
        assertNotNull(exportContext);

        final var module = exportContext.module();
        assertNotNull(module);
        assertEquals("module1", module.getName());
        assertEquals(Revision.ofNullable("2014-01-01"), module.getRevision());
        assertEquals("module:1", module.getNamespace().toString());
    }

    /**
     * Get schema with identifier of not-existing module. Trying to create <code>SchemaExportContext</code> with
     * not-existing module should result in error.
     */
    @Test
    public void getSchemaForNotExistingModuleTest() {
        // prepare conditions - return not-mount point schema context
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT);

        // make test & verify
        final var ex = assertThrows(RestconfDocumentedException.class, () ->
            schemaService.getSchema(NOT_EXISTING_MODULE));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Module not-existing 2016-01-01 cannot be found on controller.", error.getErrorMessage());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
    }

    /**
     * Get schema with identifier of existing module behind mount point and check if correct module was found.
     */
    @Test
    public void getSchemaMountPointTest() {
        // prepare conditions - return schema context with mount points
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS);

        // make test
        final var exportContext = schemaService.getSchema(MOUNT_POINT + TEST_MODULE_BEHIND_MOUNT_POINT);

        // verify
        assertNotNull(exportContext);

        final var module = exportContext.module();
        assertNotNull(module);

        assertEquals("module1-behind-mount-point", module.getName());
        assertEquals(Revision.ofNullable("2014-02-03"), module.getRevision());
        assertEquals("module:1:behind:mount:point", module.getNamespace().toString());
    }

    /**
     * Get schema with identifier of not-existing module behind mount point. Trying to create
     * <code>SchemaExportContext</code> with not-existing module behind mount point should result in error.
     */
    @Test
    public void getSchemaForNotExistingModuleMountPointTest() {
        // prepare conditions - return schema context with mount points
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS);

        // make test & verify
        final var ex = assertThrows(RestconfDocumentedException.class, () ->
            schemaService.getSchema(MOUNT_POINT + NOT_EXISTING_MODULE));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Module not-existing 2016-01-01 cannot be found on /(mount:point:1?revision=2016-01-01)cont.",
            error.getErrorMessage());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
    }

    /**
     * Try to get schema with <code>null</code> <code>SchemaContext</code> expecting <code>NullPointerException</code>.
     */
    @Test
    public void getSchemaWithNullSchemaContextTest() {
        // prepare conditions - returned schema context is null
        when(mockSchemaService.getGlobalContext()).thenReturn(null);

        // make test
        assertThrows(NullPointerException.class, () -> schemaService.getSchema(TEST_MODULE));
    }

    /**
     * Try to get schema with <code>null</code> <code>SchemaContext</code> for mount points.
     * <code>NullPointerException</code> is expected.
     */
    @Test
    public void getSchemaWithNullSchemaContextMountPointTest() {
        // prepare conditions - returned schema context for mount points is null
        when(mockSchemaService.getGlobalContext()).thenReturn(null);

        // make test
        assertThrows(NullPointerException.class,
            () -> schemaService.getSchema(MOUNT_POINT + TEST_MODULE_BEHIND_MOUNT_POINT));
    }

    /**
     * Try to get schema with <code>null</code> <code>SchemaContext</code> behind mount point when using
     * <code>NULL_MOUNT_POINT</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getSchemaNullSchemaContextBehindMountPointTest() {
        // prepare conditions - return correct schema context for mount points (this is not null)
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS);

        // make test - call service on mount point with null schema context
        assertThrows(IllegalStateException.class,
            // NULL_MOUNT_POINT contains null schema context
            () -> schemaService.getSchema(NULL_MOUNT_POINT + TEST_MODULE_BEHIND_MOUNT_POINT));
    }

    /**
     * Try to get schema with null identifier expecting <code>NullPointerException</code>. The same processing is for
     * server and also for mount point.
     */
    @Test
    public void getSchemaWithNullIdentifierTest() {
        // prepare conditions - return correct schema context
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT);

        // make test
        assertThrows(NullPointerException.class, () -> schemaService.getSchema(null));
    }

    /**
     * Try to get schema with empty (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void getSchemaWithEmptyIdentifierTest() {
        // prepare conditions - return correct schema context
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT);

        // make test and verify
        final var ex = assertThrows(RestconfDocumentedException.class, () -> schemaService.getSchema(""));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema with empty (not valid) identifier behind mount point catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithEmptyIdentifierMountPointTest() {
        // prepare conditions - return correct schema context with mount points
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS);

        // make test and verify
        final var ex = assertThrows(RestconfDocumentedException.class, () -> schemaService.getSchema(MOUNT_POINT + ""));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema with not-parsable identifier catching <code>RestconfDocumentedException</code>. Error type,
     * error tag and error status code are compared to expected values.
     */
    @Test
    public void getSchemaWithNotParsableIdentifierTest() {
        // prepare conditions - return correct schema context without mount points
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT);

        // make test and verify
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> schemaService.getSchema("01_module/2016-01-01"));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema behind mount point with not-parsable identifier catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithNotParsableIdentifierMountPointTest() {
        // prepare conditions - return correct schema context with mount points
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS);

        // make test and verify
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> schemaService.getSchema(MOUNT_POINT + "01_module/2016-01-01"));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
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
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT);

        // make test and verify
        final var ex = assertThrows(RestconfDocumentedException.class, () -> schemaService.getSchema("2014-01-01"));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
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
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS);

        // make test and verify
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> schemaService.getSchema(MOUNT_POINT + "2014-01-01"));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema with identifier which does not contain revision catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithoutRevisionTest() {
        // prepare conditions - return correct schema context without mount points
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT);

        // make test and verify
        final var ex = assertThrows(RestconfDocumentedException.class, () -> schemaService.getSchema("module"));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Revision date must be supplied.", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema behind mount point with identifier when does not contain revision catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithoutRevisionMountPointTest() {
        // prepare conditions - return correct schema context with mount points
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS);

        // make test and verify
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> schemaService.getSchema(MOUNT_POINT + "module"));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Revision date must be supplied.", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Negative test when mount point module is not found in current <code>SchemaContext</code> for mount points.
     * <code>IllegalArgumentException</code> exception is expected.
     */
    @Test
    public void getSchemaContextWithNotExistingMountPointTest() {
        // prepare conditions - return schema context with mount points
        when(mockSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS);

        // make test
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> schemaService.getSchema(NOT_EXISTING_MOUNT_POINT + TEST_MODULE_BEHIND_MOUNT_POINT));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Failed to lookup for module with name 'mount-point-3'.", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, error.getErrorTag());
    }
}
