/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2023 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class RestconfModulesGetTest {
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/parser-identifier");
    private static final EffectiveModelContext MODEL_CONTEXT_ON_MOUNT_POINT =
        YangParserTestUtils.parseYangResourceDirectory("/parser-identifier");
    private static final String TEST_MODULE_NAME = "test-module";
    private static final String TEST_MODULE_REVISION = "2016-06-02";
    private static final String TEST_MODULE_NAMESPACE = "test:module";
    private static final String MOUNT_POINT_IDENT = "mount-point:mount-container/point-number/yang-ext:mount";
    private static final YangInstanceIdentifier MOUNT_IID = YangInstanceIdentifier.of(
        QName.create("mount:point", "2016-06-02", "mount-container"),
        QName.create("mount:point", "2016-06-02", "point-number"));

    @Mock
    private DOMYangTextSourceProvider sourceProvider;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private DOMMountPointService mountPointService;

    /**
     * Positive test of getting <code>SchemaExportContext</code>. Expected module name, revision and namespace are
     * verified.
     */
    @Test
    void toSchemaExportContextFromIdentifierTest() {
        final var exportContext = RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(
                MODEL_CONTEXT, TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, null, sourceProvider);
        assertNotNull(exportContext);

        final var module = exportContext.module();
        assertNotNull(module);
        assertEquals(TEST_MODULE_NAME, module.argument().getLocalName());
        final var namespace = module.localQNameModule();
        assertEquals(Revision.ofNullable(TEST_MODULE_REVISION), namespace.getRevision());
        assertEquals(TEST_MODULE_NAMESPACE, namespace.getNamespace().toString());
    }

    /**
     * Test of getting <code>SchemaExportContext</code> when desired module is not found.
     * <code>SchemaExportContext</code> should not be created and exception is thrown.
     */
    @Test
    void toSchemaExportContextFromIdentifierNotFoundTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(
                MODEL_CONTEXT, "not-existing-module" + "/" + "2016-01-01", null, sourceProvider));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Module not-existing-module 2016-01-01 cannot be found on controller.", error.getErrorMessage());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
    }

    /**
     * Negative test trying to get <code>SchemaExportContext</code> with invalid identifier. Test is expected to fail
     * with <code>RestconfDocumentedException</code> error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    void toSchemaExportContextFromIdentifierInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
                TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME, null, sourceProvider));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Positive test of getting <code>SchemaExportContext</code> for module behind mount point.
     * Expected module name, revision and namespace are verified.
     */
    @Test
    void toSchemaExportContextFromIdentifierMountPointTest() {
        mockMountPoint();

        final var exportContext = RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
            MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION,
            mountPointService, sourceProvider);

        final var module = exportContext.module();
        assertEquals(TEST_MODULE_NAME, module.argument().getLocalName());
        final var namespace = module.localQNameModule();
        assertEquals(Revision.ofNullable(TEST_MODULE_REVISION), namespace.getRevision());
        assertEquals(TEST_MODULE_NAMESPACE, namespace.getNamespace().toString());
    }

    /**
     * Negative test of getting <code>SchemaExportContext</code> when desired module is not found behind mount point.
     * <code>SchemaExportContext</code> should not be created and exception is thrown.
     */
    @Test
    void toSchemaExportContextFromIdentifierMountPointNotFoundTest() {
        mockMountPoint();
        doReturn(MOUNT_IID).when(mountPoint).getIdentifier();

        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
                MOUNT_POINT_IDENT + "/" + "not-existing-module" + "/" + "2016-01-01",
                mountPointService, sourceProvider));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Module not-existing-module 2016-01-01 cannot be found on "
            + "/(mount:point?revision=2016-06-02)mount-container/point-number.", error.getErrorMessage());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
    }

    /**
     * Negative test trying to get <code>SchemaExportContext</code> behind mount point with invalid identifier. Test is
     * expected to fail with <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    void toSchemaExportContextFromIdentifierMountPointInvalidIdentifierNegativeTest() {
        mockMountPoint();

        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
                MOUNT_POINT_IDENT + "/" + TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME, mountPointService,
                sourceProvider));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Negative test of getting <code>SchemaExportContext</code> when supplied identifier is null.
     * <code>NullPointerException</code> is expected. <code>DOMMountPointService</code> is not used.
     */
    @Test
    void toSchemaExportContextFromIdentifierNullIdentifierNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(MODEL_CONTEXT, null, null,
                sourceProvider));
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>SchemaContext</code> is
     * <code>null</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    void toSchemaExportContextFromIdentifierNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(null,
                TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, null, sourceProvider));
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>SchemaContext</code> is
     * <code>null</code> and identifier specifies module behind mount point. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    void toSchemaExportContextFromIdentifierMountPointNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(null,
                MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, mountPointService,
                sourceProvider));
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>DOMMountPointService</code>
     * is <code>null</code> and identifier defines module behind mount point. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    void toSchemaExportContextFromIdentifierNullMountPointServiceNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(
                MODEL_CONTEXT, MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, null,
                sourceProvider));
    }

    @Test
    void toSchemaExportContextFromIdentifierNullSchemaContextBehindMountPointNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> RestconfSchemaServiceImpl.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
                "/yang-ext:mount/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, mountPointService,
                sourceProvider));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        // FIXME: this should be something different
        assertEquals("Identifier may not be empty", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    private void mockMountPoint() {
        doReturn(Optional.of(FixedDOMSchemaService.of(MODEL_CONTEXT_ON_MOUNT_POINT))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(MOUNT_IID);
    }
}
