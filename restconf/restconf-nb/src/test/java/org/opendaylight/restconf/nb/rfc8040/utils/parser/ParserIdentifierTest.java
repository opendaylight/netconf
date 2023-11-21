/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.legacy.ErrorTags;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link ParserIdentifier}.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ParserIdentifierTest {
    // mount point identifier
    private static final String MOUNT_POINT_IDENT = "mount-point:mount-container/point-number/yang-ext:mount";

    // invalid mount point identifier
    private static final String INVALID_MOUNT_POINT_IDENT = "mount-point:point-number/yang-ext:mount";

    // test identifier + expected result
    private static final String TEST_IDENT =
            "parser-identifier:cont1/cont2/listTest/list-in-grouping=name/leaf-A.B";

    private static final String TEST_IDENT_RESULT = """
            /(parser:identifier?revision=2016-06-02)cont1/cont2/listTest/listTest/list-in-grouping/\
            list-in-grouping[{(parser:identifier?revision=2016-06-02)name=name}]/leaf-A.B""";

    // test identifier with nodes defined in other modules using augmentation + expected result
    private static final String TEST_IDENT_OTHERS =
            "parser-identifier-included:list-1=name,2016-06-02/parser-identifier:augment-leaf";

    private static final String TEST_IDENT_OTHERS_RESULT = """
        /(parser:identifier:included?revision=2016-06-02)list-1/list-1\
        [{(parser:identifier:included?revision=2016-06-02)name=name, \
        (parser:identifier:included?revision=2016-06-02)revision=2016-06-02}]\
        /(parser:identifier?revision=2016-06-02)augment-leaf""";

    // invalid test identifier
    private static final String INVALID_TEST_IDENT =
            "parser-identifier:cont2/listTest/list-in-grouping=name/leaf-A.B";

    private static final String TEST_MODULE_NAME = "test-module";
    private static final String TEST_MODULE_REVISION = "2016-06-02";
    private static final String TEST_MODULE_NAMESPACE = "test:module";

    private static final String INVOKE_RPC = "invoke-rpc-module:rpc-test";
    private static final String INVOKE_ACTION = "example-actions:interfaces/interface=eth0/reset";

    // schema context with test modules
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/parser-identifier");
    // contains the same modules but it is different object (it can be compared with equals)
    // FIXME: we really should use a different context of mount point
    private static final EffectiveModelContext MODEL_CONTEXT_ON_MOUNT_POINT =
        YangParserTestUtils.parseYangResourceDirectory("/parser-identifier");

    // mount point and mount point service
    private DOMMountPoint mountPoint;
    private DOMMountPointService mountPointService;

    // mock mount point and mount point service
    @Mock
    private DOMMountPoint mockMountPoint;
    @Mock
    private DOMMountPointService mockMountPointService;
    @Mock
    private DOMSchemaService domSchemaService;
    @Mock
    private DOMYangTextSourceProvider sourceProvider;

    @Before
    public void setup() throws Exception {
        mountPointService = new DOMMountPointServiceImpl();

        // create and register mount point
        final var mountPointId = YangInstanceIdentifier.builder()
                .node(QName.create("mount:point", "2016-06-02", "mount-container"))
                .node(QName.create("mount:point", "2016-06-02", "point-number"))
                .build();

        mountPoint = mountPointService.createMountPoint(mountPointId)
                .addService(DOMSchemaService.class, FixedDOMSchemaService.of(MODEL_CONTEXT_ON_MOUNT_POINT))
                .register()
                .getInstance();

        // register mount point with null schema context
        when(mockMountPointService.getMountPoint(YangInstanceIdentifier.of()))
                .thenReturn(Optional.of(mockMountPoint));
    }

    /**
     * {@link ParserIdentifier#toInstanceIdentifier(String, SchemaContext)} tests.
     */

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when all nodes are defined
     * in one module.
     */
    @Test
    public void toInstanceIdentifierTest() {
        final var context = ParserIdentifier.toInstanceIdentifier(TEST_IDENT, MODEL_CONTEXT, null);
        assertEquals(TEST_IDENT_RESULT, context.getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when nodes are defined in
     * multiple modules.
     */
    @Test
    public void toInstanceIdentifierOtherModulesTest() {
        final var context = ParserIdentifier.toInstanceIdentifier(TEST_IDENT_OTHERS, MODEL_CONTEXT, null);
        assertEquals(TEST_IDENT_OTHERS_RESULT, context.getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating {@code InstanceIdentifierContext} from identifier containing {@code yang-ext:mount}.
     */
    @Test
    public void toInstanceIdentifierMountPointTest() {
        final var context = ParserIdentifier.toInstanceIdentifier(MOUNT_POINT_IDENT + "/" + TEST_IDENT, MODEL_CONTEXT,
            mountPointService);
        assertEquals(TEST_IDENT_RESULT.toString(), context.getInstanceIdentifier().toString());
        assertEquals(mountPoint, context.getMountPoint());
        assertEquals(MODEL_CONTEXT_ON_MOUNT_POINT, context.getSchemaContext());
    }

    /**
     * Test of creating <code>InstanceIdentifierContext</code> when identifier is <code>null</code>.
     * <code>{@link YangInstanceIdentifier#empty()}</code> should be returned.
     */
    @Test
    public void toInstanceIdentifierNullIdentifierTest() {
        final var context = ParserIdentifier.toInstanceIdentifier(null, MODEL_CONTEXT, null);
        assertEquals(YangInstanceIdentifier.of(), context.getInstanceIdentifier());
    }

    /**
     * Negative test of creating <code>InstanceIdentifierContext</code> when <code>SchemaContext</code> is
     * <code>null</code>. Test fails expecting <code>NullPointerException</code>.
     */
    @Test
    public void toInstanceIdentifierNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class, () -> ParserIdentifier.toInstanceIdentifier(TEST_IDENT, null, null));
    }

    /**
     * Api path can be empty. <code>YangInstanceIdentifier.EMPTY</code> is expected to be returned.
     */
    @Test
    public void toInstanceIdentifierEmptyIdentifierTest() {
        final var context = ParserIdentifier.toInstanceIdentifier("", MODEL_CONTEXT, null);
        assertEquals(YangInstanceIdentifier.of(), context.getInstanceIdentifier());
    }

    /**
     * Negative test with invalid test identifier. Test should fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void toInstanceIdentifierInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toInstanceIdentifier(INVALID_TEST_IDENT, MODEL_CONTEXT, null));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Schema for '(parser:identifier?revision=2016-06-02)cont2' not found", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Negative test when identifier contains {@code yang-ext:mount} but identifier part is not valid. Test
     * should fail with {@link RestconfDocumentedException}.
     */
    @Test
    public void toInstanceIdentifierMountPointInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toInstanceIdentifier(INVALID_MOUNT_POINT_IDENT, MODEL_CONTEXT, mountPointService));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Schema for '(mount:point?revision=2016-06-02)point-number' not found", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Negative test when <code>DOMMountPoint</code> cannot be found. Test is expected to fail with
     * <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void toInstanceIdentifierMissingMountPointNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toInstanceIdentifier("/yang-ext:mount", MODEL_CONTEXT, mountPointService));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Mount point does not exist.", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTags.RESOURCE_DENIED_TRANSPORT, error.getErrorTag());
    }

    /**
     * Negative test when <code>{@link DOMMountPointService}</code> is absent. Test is expected to fail with
     * <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void toInstanceIdentifierMissingMountPointServiceNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toInstanceIdentifier("yang-ext:mount", MODEL_CONTEXT, null));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Mount point service is not available", error.getErrorMessage());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
    }

    /**
     * {@link ParserIdentifier#makeQNameFromIdentifier(String)} tests.
     */

    /**
     * Positive test of making <code>QName</code> from identifier and compare values from returned <code>QName</code>
     * to expected values.
     */
    @Test
    public void makeQNameFromIdentifierTest() {
        final var qname = ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION);
        assertNotNull(qname);
        assertEquals(TEST_MODULE_NAME, qname.getKey());
        assertEquals(Revision.of(TEST_MODULE_REVISION), qname.getValue());
    }

    /**
     * Negative test when supplied identifier is in invalid format and then revision is not parsable.
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("URI has bad format. It should be 'moduleName/yyyy-MM-dd'", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Negative test when supplied identifier is too short (contains only module name).
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierTooShortIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_NAME));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("URI has bad format. End of URI should be in format 'moduleName/yyyy-MM-dd'",
            error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Positive test of making <code>QName</code> from identifier for module behind mount point. Value from returned
     * <code>QName</code> are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountTest() {
        final var qname = ParserIdentifier.makeQNameFromIdentifier(
            MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION);

        assertNotNull(qname);
        assertEquals(TEST_MODULE_NAME, qname.getKey());
        assertEquals(Revision.of(TEST_MODULE_REVISION), qname.getValue());
    }

    /**
     * Negative test when supplied identifier for module behind mount point is in invalid format and then revision is
     * not parsable. <code>RestconfDocumentedException</code> is expected and error type, error tag and error status
     * code are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountPointInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.makeQNameFromIdentifier(
                    MOUNT_POINT_IDENT + "/" + TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("URI has bad format. It should be 'moduleName/yyyy-MM-dd'", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Negative test when supplied identifier for module behind mount point is too short (contains only module name).
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code
     * are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountPointTooShortIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.makeQNameFromIdentifier(MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("URI has bad format. End of URI should be in format 'moduleName/yyyy-MM-dd'",
            error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Negative test trying to make <code>QName</code> from <code>null</code> identifier. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void makeQNameFromIdentifierNullIdentifierNegativeTest() {
        assertThrows(NullPointerException.class, () -> ParserIdentifier.makeQNameFromIdentifier(null));
    }

    /**
     * Negative test trying to make <code>QName</code> from empty identifier. Test is expected to fail with
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code is compared to expected
     * values.
     */
    @Test
    public void makeQNameFromIdentifierEmptyIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.makeQNameFromIdentifier(""));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("URI has bad format. End of URI should be in format 'moduleName/yyyy-MM-dd'",
            error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Negative test with identifier containing double slash. Between // there is one empty string which will be
     * incorrectly considered to be module revision. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    public void makeQNameFromIdentifierDoubleSlashNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_NAME + "//" + TEST_MODULE_REVISION));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("URI has bad format. End of URI should be in format 'moduleName/yyyy-MM-dd'",
            error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * {@link ParserIdentifier#toSchemaExportContextFromIdentifier(SchemaContext, String, DOMMountPointService)} tests.
     */

    /**
     * Positive test of getting <code>SchemaExportContext</code>. Expected module name, revision and namespace are
     * verified.
     */
    @Test
    public void toSchemaExportContextFromIdentifierTest() {
        final var exportContext = ParserIdentifier.toSchemaExportContextFromIdentifier(
                MODEL_CONTEXT, TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, null, sourceProvider);

        assertNotNull("Export context should be parsed", exportContext);

        final var module = exportContext.module();
        assertNotNull(module);
        assertEquals(TEST_MODULE_NAME, module.getName());
        assertEquals(Revision.ofNullable(TEST_MODULE_REVISION), module.getRevision());
        assertEquals(TEST_MODULE_NAMESPACE, module.getNamespace().toString());
    }

    /**
     * Test of getting <code>SchemaExportContext</code> when desired module is not found.
     * <code>SchemaExportContext</code> should not be created and exception is thrown.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNotFoundTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toSchemaExportContextFromIdentifier(
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
    public void toSchemaExportContextFromIdentifierInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
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
    public void toSchemaExportContextFromIdentifierMountPointTest() {
        final var exportContext = ParserIdentifier.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
            MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION,
            mountPointService, sourceProvider);

        final var module = exportContext.module();
        assertNotNull(module);

        assertEquals(TEST_MODULE_NAME, module.getName());
        assertEquals(Revision.ofNullable(TEST_MODULE_REVISION), module.getRevision());
        assertEquals(TEST_MODULE_NAMESPACE, module.getNamespace().toString());
    }

    /**
     * Negative test of getting <code>SchemaExportContext</code> when desired module is not found behind mount point.
     * <code>SchemaExportContext</code> should not be created and exception is thrown.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointNotFoundTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
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
    public void toSchemaExportContextFromIdentifierMountPointInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toSchemaExportContextFromIdentifier(MODEL_CONTEXT,
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
    public void toSchemaExportContextFromIdentifierNullIdentifierNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> ParserIdentifier.toSchemaExportContextFromIdentifier(MODEL_CONTEXT, null, null, sourceProvider));
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>SchemaContext</code> is
     * <code>null</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class, () -> ParserIdentifier.toSchemaExportContextFromIdentifier(null,
            TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, null, sourceProvider));
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>SchemaContext</code> is
     * <code>null</code> and identifier specifies module behind mount point. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class, () -> ParserIdentifier.toSchemaExportContextFromIdentifier(null,
            MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, mountPointService,
            sourceProvider));
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>DOMMountPointService</code>
     * is <code>null</code> and identifier defines module behind mount point. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNullMountPointServiceNegativeTest() {
        assertThrows(NullPointerException.class, () -> ParserIdentifier.toSchemaExportContextFromIdentifier(
            MODEL_CONTEXT, MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, null,
            sourceProvider));
    }

    @Test
    public void toSchemaExportContextFromIdentifierNullSchemaContextBehindMountPointNegativeTest() {
        assertThrows(IllegalStateException.class, () -> ParserIdentifier.toSchemaExportContextFromIdentifier(
                MODEL_CONTEXT, "/yang-ext:mount/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION,
                mockMountPointService, sourceProvider));
    }

    /**
     * Test invoke RPC.
     *
     * <p>
     * Verify if RPC schema node was found.
     */
    @Test
    public void invokeRpcTest() {
        final var result = ParserIdentifier.toInstanceIdentifier(INVOKE_RPC, MODEL_CONTEXT, null);

        // RPC schema node
        final QName rpcQName = result.getSchemaNode().getQName();
        assertEquals("invoke:rpc:module", rpcQName.getModule().getNamespace().toString());
        assertEquals("rpc-test", rpcQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_RPC, MODEL_CONTEXT), result.getInstanceIdentifier());
        assertEquals(null, result.getMountPoint());
        assertEquals(MODEL_CONTEXT, result.getSchemaContext());
    }

    /**
     * Test invoke RPC on mount point.
     *
     * <p>
     * Verify if RPC schema node was found.
     */
    @Test
    public void invokeRpcOnMountPointTest() {
        final var result = ParserIdentifier.toInstanceIdentifier(MOUNT_POINT_IDENT + "/" + INVOKE_RPC, MODEL_CONTEXT,
            mountPointService);

        // RPC schema node
        final QName rpcQName = result.getSchemaNode().getQName();
        assertEquals("invoke:rpc:module", rpcQName.getModule().getNamespace().toString());
        assertEquals("rpc-test", rpcQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_RPC, MODEL_CONTEXT), result.getInstanceIdentifier());
        assertEquals(mountPoint, result.getMountPoint());
        assertEquals(MODEL_CONTEXT_ON_MOUNT_POINT, result.getSchemaContext());
    }

    /**
     * Test Action.
     * Verify if Action schema node was found.
     */
    @Test
    public void invokeActionTest() {
        final var result = ParserIdentifier.toInstanceIdentifier(INVOKE_ACTION, MODEL_CONTEXT, null);

        // Action schema node
        final QName actionQName = result.getSchemaNode().getQName();
        assertEquals("https://example.com/ns/example-actions", actionQName.getModule().getNamespace().toString());
        assertEquals("reset", actionQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_ACTION, MODEL_CONTEXT), result.getInstanceIdentifier());
        assertNull(result.getMountPoint());
        assertSame(MODEL_CONTEXT, result.getSchemaContext());
    }

    /**
     * Test invoke Action on mount point.
     * Verify if Action schema node was found.
     */
    @Test
    public void invokeActionOnMountPointTest() {
        final var result = ParserIdentifier.toInstanceIdentifier(MOUNT_POINT_IDENT + "/" + INVOKE_ACTION,
            MODEL_CONTEXT, mountPointService);

        // Action schema node
        final QName actionQName = result.getSchemaNode().getQName();
        assertEquals("https://example.com/ns/example-actions", actionQName.getModule().getNamespace().toString());
        assertEquals("reset", actionQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_ACTION, MODEL_CONTEXT), result.getInstanceIdentifier());
        assertEquals(mountPoint, result.getMountPoint());
        assertEquals(MODEL_CONTEXT_ON_MOUNT_POINT, result.getSchemaContext());
    }
}
