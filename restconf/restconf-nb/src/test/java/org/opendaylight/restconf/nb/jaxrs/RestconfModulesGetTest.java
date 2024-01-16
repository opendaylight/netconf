/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2023 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import java.util.function.Consumer;
import javax.ws.rs.container.AsyncResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.legacy.ErrorTags;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class RestconfModulesGetTest extends AbstractRestconfTest {
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/parser-identifier");
    private static final EffectiveModelContext MODEL_CONTEXT_ON_MOUNT_POINT =
        YangParserTestUtils.parseYangResourceDirectory("/parser-identifier");
    private static final String TEST_MODULE_NAME = "test-module";
    private static final String TEST_MODULE_REVISION = "2016-06-02";
    private static final String TEST_MODULE_NAMESPACE = "test:module";
    private static final ApiPath MOUNT_POINT_IDENT = apiPath("mount-point:mount-container/point-number/yang-ext:mount");
    private static final YangInstanceIdentifier MOUNT_IID = YangInstanceIdentifier.of(
        QName.create("mount:point", "2016-06-02", "mount-container"),
        QName.create("mount:point", "2016-06-02", "point-number"));

    @Mock
    private YangTextSourceExtension sourceProvider;
    @Mock
    private DOMSchemaService schemaService;

    @Override
    EffectiveModelContext modelContext() {
        return MODEL_CONTEXT;
    }

    /**
     * Positive test of getting <code>SchemaExportContext</code>. Expected module name, revision and namespace are
     * verified.
     */
    @Test
    void toSchemaExportContextFromIdentifierTest() {
        assertEquals("""
            module test-module {
              namespace test:module;
              prefix testm;
              yang-version 1;
              revision 2016-06-02 {
                description
                  "Initial revision.";
              }
            }
            """, assertYang(null, TEST_MODULE_NAME, TEST_MODULE_REVISION));
    }

    /**
     * Test of getting <code>SchemaExportContext</code> when desired module is not found.
     * <code>SchemaExportContext</code> should not be created and exception is thrown.
     */
    @Test
    void toSchemaExportContextFromIdentifierNotFoundTest() {
        final var error = assertError(ar -> restconf.modulesYinGET("not-existing-module", "2016-01-01", ar));
        assertEquals("Source not-existing-module@2016-01-01 not found", error.getErrorMessage());
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
        final var error = assertError(ar -> restconf.modulesYangGET(TEST_MODULE_REVISION, TEST_MODULE_NAME, ar));
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

        final var content = assertYang(MOUNT_POINT_IDENT, TEST_MODULE_NAME, TEST_MODULE_REVISION);
        assertEquals("""
            module test-module {
              namespace test:module;
              prefix testm;
              yang-version 1;
              revision 2016-06-02 {
                description
                  "Initial revision.";
              }
            }
            """, content);
    }

    /**
     * Negative test of getting <code>SchemaExportContext</code> when desired module is not found behind mount point.
     * <code>SchemaExportContext</code> should not be created and exception is thrown.
     */
    @Test
    void toSchemaExportContextFromIdentifierMountPointNotFoundTest() {
        mockMountPoint();

        final var error = assertError(
            ar -> restconf.modulesYangGET(MOUNT_POINT_IDENT, "not-existing-module", "2016-01-01", ar));
        assertEquals("Source not-existing-module@2016-01-01 not found", error.getErrorMessage());
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

        final var error = assertError(
            ar -> restconf.modulesYangGET(MOUNT_POINT_IDENT, TEST_MODULE_REVISION, TEST_MODULE_NAME, ar));
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    @Test
    void toSchemaExportContextFromIdentifierNullSchemaContextBehindMountPointNegativeTest() {
        doReturn(Optional.of(schemaService)).when(mountPoint).getService(DOMSchemaService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(MOUNT_IID);

        final var error = assertError(
            ar -> restconf.modulesYangGET(MOUNT_POINT_IDENT, TEST_MODULE_NAME, TEST_MODULE_REVISION, ar));
        assertEquals("Mount point 'mount-point:mount-container/point-number' does not have any models",
            error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTags.RESOURCE_DENIED_TRANSPORT, error.getErrorTag());
    }

    private void mockMountPoint() {
        doReturn(Optional.of(new FixedDOMSchemaService(MODEL_CONTEXT_ON_MOUNT_POINT))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(MOUNT_IID);
        doReturn(Optional.of(mountPointService)).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
    }

    /**
     * Negative test of module revision validation when there is no revision. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    void validateAndGetRevisionNotSuppliedTest() {
        final var error = assertError(ar -> restconf.modulesYangGET("module", null, ar));
        assertEquals("Source module not found", error.getErrorMessage());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Negative test of module revision validation when supplied revision is not parsable as revision. Test fails
     * catching <code>RestconfDocumentedException</code>.
     */
    @Test
    void validateAndGetRevisionNotParsableTest() {
        final var error = assertInvalidValue("module", "not-parsable-as-date");
        assertEquals("Supplied revision is not in expected date format YYYY-mm-dd", error.getErrorMessage());
    }

    /**
     * Negative test of module name validation when there is no module name. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    void validateAndGetModulNameNotSuppliedTest() {
        final var error = assertInvalidValue(null, null);
        assertEquals("Module name must be supplied", error.getErrorMessage());
    }

    /**
     * Negative test of module name validation when supplied name is not parsable as module name on the first
     * character. Test fails catching <code>RestconfDocumentedException</code> and checking for correct error type,
     * error tag and error status code.
     */
    @Test
    void validateAndGetModuleNameNotParsableFirstTest() {
        final var error = assertInvalidValue("01-not-parsable-as-name-on-first-char", null);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
    }

    /**
     * Negative test of module name validation when supplied name is not parsable as module name on any of the
     * characters after the first character. Test fails catching <code>RestconfDocumentedException</code> and checking
     * for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetModuleNameNotParsableNextTest() {
        final var error = assertInvalidValue("not-parsable-as-name-after-first-char*", null);
        assertEquals("Supplied name has not expected identifier format", error.getErrorMessage());
    }

    /**
     * Negative test of module name validation when supplied name is empty. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    void validateAndGetModuleNameEmptyTest() {
        final var error = assertInvalidValue("", null);
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
    }

    private String assertYang(final ApiPath mountPath, final String fileName, final String revision) {
        final Consumer<AsyncResponse> invocation = mountPath != null
            ? ar -> restconf.modulesYangGET(mountPath, fileName, revision, ar)
                : ar -> restconf.modulesYangGET(fileName, revision, ar);
        try (var reader = assertEntity(Reader.class, 200, invocation)) {
            return CharStreams.toString(reader);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private RestconfError assertInvalidValue(final String fileName, final String revision) {
        final var error = assertError(ar -> restconf.modulesYangGET(fileName, revision, ar));
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
        return error;
    }
}
