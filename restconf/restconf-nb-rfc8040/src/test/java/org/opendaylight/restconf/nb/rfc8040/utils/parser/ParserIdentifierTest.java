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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map.Entry;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
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

    private static final String TEST_IDENT_RESULT =
            "/(parser:identifier?revision=2016-06-02)cont1/cont2/listTest/listTest/list-in-grouping/"
            + "list-in-grouping[{(parser:identifier?revision=2016-06-02)name=name}]/leaf-A.B";

    // test identifier with nodes defined in other modules using augmentation + expected result
    private static final String TEST_IDENT_OTHERS =
            "parser-identifier-included:list-1=name,2016-06-02/parser-identifier:augment-leaf";

    private static final String TEST_IDENT_OTHERS_RESULT =
            "/(parser:identifier:included?revision=2016-06-02)list-1/list-1"
            + "[{(parser:identifier:included?revision=2016-06-02)name=name, "
            + "(parser:identifier:included?revision=2016-06-02)revision=2016-06-02}]"
            + "/AugmentationIdentifier{childNames=[(parser:identifier?revision=2016-06-02)augment-leaf]}/"
            + "(parser:identifier?revision=2016-06-02)augment-leaf";

    // invalid test identifier
    private static final String INVALID_TEST_IDENT =
            "parser-identifier:cont2/listTest/list-in-grouping=name/leaf-A.B";

    private static final String TEST_MODULE_NAME = "test-module";
    private static final String TEST_MODULE_REVISION = "2016-06-02";
    private static final String TEST_MODULE_NAMESPACE = "test:module";

    private static final String INVOKE_RPC = "invoke-rpc-module:rpc-test";
    private static final String INVOKE_ACTION = "example-actions:interfaces/interface=eth0/reset";

    // schema context with test modules
    private static EffectiveModelContext SCHEMA_CONTEXT;
    // contains the same modules but it is different object (it can be compared with equals)
    private static EffectiveModelContext SCHEMA_CONTEXT_ON_MOUNT_POINT;

    // mount point and mount point service
    private ParserIdentifier parserIdentifier;

    // mock mount point and mount point service
    @Mock
    private DOMMountPoint mockMountPoint;
    @Mock
    private DOMMountPointService mockMountPointService;
    @Mock
    private DOMYangTextSourceProvider sourceProvider;
    @Mock
    private DOMSchemaService mockMountPointSchemaService;
    private SchemaContextHandler schemaContextHandler;

    @BeforeClass
    public static void beforeClass() throws Exception {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/parser-identifier"));
        SCHEMA_CONTEXT_ON_MOUNT_POINT =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/parser-identifier"));
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
        SCHEMA_CONTEXT_ON_MOUNT_POINT = null;
    }

    @Before
    public void setup() throws Exception {
        // create and register mount point
        final YangInstanceIdentifier mountPointId = YangInstanceIdentifier.builder()
                .node(QName.create("mount:point", "2016-06-02", "mount-container"))
                .node(QName.create("mount:point", "2016-06-02", "point-number"))
                .build();

        // register mount point with null schema context
        when(mockMountPointSchemaService.getGlobalContext()).thenReturn(SCHEMA_CONTEXT_ON_MOUNT_POINT);
        when(mockMountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(mockMountPointSchemaService));
        when(mockMountPointService.getMountPoint(mountPointId)).thenReturn(Optional.of(mockMountPoint));

        schemaContextHandler = mock(SchemaContextHandler.class);
        when(schemaContextHandler.get()).thenReturn(SCHEMA_CONTEXT);
        parserIdentifier = new ParserIdentifier(mockMountPointService, schemaContextHandler, sourceProvider);
    }

    /*
     * {@link ParserIdentifier#toInstanceIdentifier(String, SchemaContext)} tests.
     */

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when all nodes are defined
     * in one module.
     */
    @Test
    public void toInstanceIdentifierTest() {
        final InstanceIdentifierContext<?> context = parserIdentifier.toInstanceIdentifier(TEST_IDENT);
        assertEquals("Returned not expected identifier",
                TEST_IDENT_RESULT, context .getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when nodes are defined in
     * multiple modules.
     */
    @Test
    public void toInstanceIdentifierOtherModulesTest() {
        final InstanceIdentifierContext<?> context = parserIdentifier.toInstanceIdentifier(TEST_IDENT_OTHERS);
        assertEquals("Returned not expected identifier",
                TEST_IDENT_OTHERS_RESULT, context.getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier containing
     * {@link RestconfConstants#MOUNT}.
     */
    @Test
    public void toInstanceIdentifierMountPointTest() {
        final InstanceIdentifierContext<?> context = parserIdentifier.toInstanceIdentifier(
                MOUNT_POINT_IDENT + "/" + TEST_IDENT);

        assertEquals("Returned not expected identifier",
                TEST_IDENT_RESULT, context.getInstanceIdentifier().toString());
        assertEquals("Mount point not found",
                mockMountPoint, context.getMountPoint());
        assertEquals("Schema context from mount point expected",
                SCHEMA_CONTEXT_ON_MOUNT_POINT, context.getSchemaContext());
    }

    /**
     * Test of creating <code>InstanceIdentifierContext</code> when identifier is <code>null</code>.
     * <code>{@link YangInstanceIdentifier#empty()}</code> should be returned.
     */
    @Test
    public void toInstanceIdentifierNullIdentifierTest() {
        final InstanceIdentifierContext<?> context = parserIdentifier.toInstanceIdentifier(null);
        assertEquals("Returned not expected identifier",
                YangInstanceIdentifier.empty(), context.getInstanceIdentifier());
    }

    /**
     * Negative test of creating <code>InstanceIdentifierContext</code> when <code>SchemaContext</code> is
     * <code>null</code>. Test fails expecting <code>NullPointerException</code>.
     */
    @Test
    public void toInstanceIdentifierNullSchemaContextNegativeTest() {
        when(schemaContextHandler.get()).thenReturn(null);
        assertThrows(NullPointerException.class, () -> parserIdentifier.toInstanceIdentifier(TEST_IDENT));
    }

    /**
     * Api path can be empty. <code>YangInstanceIdentifier.EMPTY</code> is expected to be returned.
     */
    @Test
    public void toInstanceIdentifierEmptyIdentifierTest() {
        final InstanceIdentifierContext<?> context = parserIdentifier.toInstanceIdentifier("");
        assertEquals("Returned not expected identifier",
                YangInstanceIdentifier.empty(), context.getInstanceIdentifier());
    }

    /**
     * Negative test with invalid test identifier. Test should fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void toInstanceIdentifierInvalidIdentifierNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.toInstanceIdentifier(INVALID_TEST_IDENT));
    }

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but identifier part is not valid. Test
     * should fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void toInstanceIdentifierMountPointInvalidIdentifierNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.toInstanceIdentifier(INVALID_MOUNT_POINT_IDENT));
    }

    /**
     * Negative test when <code>DOMMountPoint</code> cannot be found. Test is expected to fail with
     * <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void toInstanceIdentifierMissingMountPointNegativeTest() {
        when(mockMountPointService.getMountPoint(any())).thenReturn(Optional.empty());
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.toInstanceIdentifier("/yang-ext:mount" + RestconfConstants.MOUNT));
        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", ErrorTag.RESOURCE_DENIED_TRANSPORT, ex.getErrors().get(0).getErrorTag());
    }

    /*
     * {@link ParserIdentifier#makeQNameFromIdentifier(String)} tests.
     */

    /**
     * Positive test of making <code>QName</code> from identifier and compare values from returned <code>QName</code>
     * to expected values.
     */
    @Test
    public void makeQNameFromIdentifierTest() {
        final Entry<String, Revision> qName = parserIdentifier.makeQNameFromIdentifier(
            TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION);

        assertNotNull("QName should be created", qName);
        assertEquals("Returned not expected module name", TEST_MODULE_NAME, qName.getKey());
        assertEquals("Returned not expected module revision", Revision.of(TEST_MODULE_REVISION), qName.getValue());
    }

    /**
     * Negative test when supplied identifier is in invalid format and then revision is not parsable.
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierInvalidIdentifierNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.makeQNameFromIdentifier(TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME));
        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", RestconfError.ErrorTag.INVALID_VALUE,
            ex.getErrors().get(0).getErrorTag());
        assertEquals("Not expected error status code", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /**
     * Negative test when supplied identifier is too short (contains only module name).
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierTooShortIdentifierNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.makeQNameFromIdentifier(TEST_MODULE_NAME));
        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", RestconfError.ErrorTag.INVALID_VALUE,
            ex.getErrors().get(0).getErrorTag());
        assertEquals("Not expected error status code", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /**
     * Positive test of making <code>QName</code> from identifier for module behind mount point. Value from returned
     * <code>QName</code> are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountTest() {
        final Entry<String, Revision> qName = parserIdentifier.makeQNameFromIdentifier(
                MOUNT_POINT_IDENT
                + "/"
                + TEST_MODULE_NAME
                + "/"
                + TEST_MODULE_REVISION);

        assertNotNull("QName should be created", qName);
        assertEquals("Returned not expected module name", TEST_MODULE_NAME, qName.getKey());
        assertEquals("Returned not expected module revision", Revision.of(TEST_MODULE_REVISION), qName.getValue());
    }

    /**
     * Negative test when supplied identifier for module behind mount point is in invalid format and then revision is
     * not parsable. <code>RestconfDocumentedException</code> is expected and error type, error tag and error status
     * code are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountPointInvalidIdentifierNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.makeQNameFromIdentifier(
                    MOUNT_POINT_IDENT + "/" + TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME));
        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", RestconfError.ErrorTag.INVALID_VALUE,
            ex.getErrors().get(0).getErrorTag());
        assertEquals("Not expected error status code", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /**
     * Negative test when supplied identifier for module behind mount point is too short (contains only module name).
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code
     * are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountPointTooShortIdentifierNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.makeQNameFromIdentifier(MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME));
        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", RestconfError.ErrorTag.INVALID_VALUE,
            ex.getErrors().get(0).getErrorTag());
        assertEquals("Not expected error status code", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /**
     * Negative test trying to make <code>QName</code> from <code>null</code> identifier. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void makeQNameFromIdentifierNullIdentifierNegativeTest() {
        assertThrows(NullPointerException.class, () -> parserIdentifier.makeQNameFromIdentifier(null));
    }

    /**
     * Negative test trying to make <code>QName</code> from empty identifier. Test is expected to fail with
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code is compared to expected
     * values.
     */
    @Test
    public void makeQNameFromIdentifierEmptyIdentifierNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.makeQNameFromIdentifier(""));
        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", RestconfError.ErrorTag.INVALID_VALUE,
            ex.getErrors().get(0).getErrorTag());
        assertEquals("Not expected error status code", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /**
     * Negative test with identifier containing double slash. Between // there is one empty string which will be
     * incorrectly considered to be module revision. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    public void makeQNameFromIdentifierDoubleSlashNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.makeQNameFromIdentifier(TEST_MODULE_NAME + "//" + TEST_MODULE_REVISION));
        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", RestconfError.ErrorTag.INVALID_VALUE,
            ex.getErrors().get(0).getErrorTag());
        assertEquals("Not expected error status code", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /*
     * {@link ParserIdentifier#toSchemaExportContextFromIdentifier(SchemaContext, String, DOMMountPointService)} tests.
     */

    /**
     * Positive test of getting <code>SchemaExportContext</code>. Expected module name, revision and namespace are
     * verified.
     */
    @Test
    public void toSchemaExportContextFromIdentifierTest() {
        final SchemaExportContext exportContext = parserIdentifier.toSchemaExportContextFromIdentifier(
                TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION);

        assertNotNull("Export context should be parsed", exportContext);

        final Module module = exportContext.getModule();
        assertNotNull("Export context should contains test module", module);

        assertEquals("Returned not expected module name",
                TEST_MODULE_NAME, module.getName());
        assertEquals("Returned not expected module revision",
                Revision.ofNullable(TEST_MODULE_REVISION), module.getRevision());
        assertEquals("Returned not expected module namespace",
                TEST_MODULE_NAMESPACE, module.getNamespace().toString());
    }

    /**
     * Test of getting <code>SchemaExportContext</code> when desired module is not found.
     * <code>SchemaExportContext</code> should be created but module should be set to <code>null</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNotFoundTest() {
        final SchemaExportContext exportContext = parserIdentifier.toSchemaExportContextFromIdentifier(
                "not-existing-module" + "/" + "2016-01-01");

        assertNotNull("Export context should be parsed", exportContext);
        assertNull("Not-existing module should be null", exportContext.getModule());
    }

    /**
     * Negative test trying to get <code>SchemaExportContext</code> with invalid identifier. Test is expected to fail
     * with <code>RestconfDocumentedException</code> error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    public void toSchemaExportContextFromIdentifierInvalidIdentifierNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.toSchemaExportContextFromIdentifier(TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME));
        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", RestconfError.ErrorTag.INVALID_VALUE,
            ex.getErrors().get(0).getErrorTag());
        assertEquals("Not expected error status code", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /**
     * Positive test of getting <code>SchemaExportContext</code> for module behind mount point.
     * Expected module name, revision and namespace are verified.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointTest() {
        final SchemaExportContext exportContext = parserIdentifier.toSchemaExportContextFromIdentifier(
                MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION);

        final Module module = exportContext.getModule();
        assertNotNull("Export context should contains test module", module);

        assertEquals("Returned not expected module name",
                TEST_MODULE_NAME, module.getName());
        assertEquals("Returned not expected module revision",
                Revision.ofNullable(TEST_MODULE_REVISION), module.getRevision());
        assertEquals("Returned not expected module namespace",
                TEST_MODULE_NAMESPACE, module.getNamespace().toString());
    }

    /**
     * Negative test of getting <code>SchemaExportContext</code> when desired module is not found behind mount point.
     * <code>SchemaExportContext</code> should be still created but module should be set to <code>null</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointNotFoundTest() {
        final SchemaExportContext exportContext = parserIdentifier.toSchemaExportContextFromIdentifier(
                MOUNT_POINT_IDENT + "/" + "not-existing-module" + "/" + "2016-01-01");

        assertNotNull("Export context should be parsed", exportContext);
        assertNull("Not-existing module should be null", exportContext.getModule());
    }

    /**
     * Negative test trying to get <code>SchemaExportContext</code> behind mount point with invalid identifier. Test is
     * expected to fail with <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointInvalidIdentifierNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parserIdentifier.toSchemaExportContextFromIdentifier(
                MOUNT_POINT_IDENT + "/" + TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME));

        assertEquals("Not expected error type", RestconfError.ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Not expected error tag", RestconfError.ErrorTag.INVALID_VALUE,
            ex.getErrors().get(0).getErrorTag());
        assertEquals("Not expected error status code", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /**
     * Negative test of getting <code>SchemaExportContext</code> when supplied identifier is null.
     * <code>NullPointerException</code> is expected. <code>DOMMountPointService</code> is not used.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNullIdentifierNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> parserIdentifier.toSchemaExportContextFromIdentifier(null));
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>SchemaContext</code> is
     * <code>null</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNullSchemaContextNegativeTest() {
        when(schemaContextHandler.get()).thenReturn(null);
        assertThrows(NullPointerException.class, () -> parserIdentifier.toSchemaExportContextFromIdentifier(
            TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION));
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>SchemaContext</code> is
     * <code>null</code> and identifier specifies module behind mount point. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointNullSchemaContextNegativeTest() {
        when(schemaContextHandler.get()).thenReturn(null);
        assertThrows(NullPointerException.class, () -> parserIdentifier.toSchemaExportContextFromIdentifier(
            MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION));
    }

    @Test
    public void toSchemaExportContextFromIdentifierNullSchemaContextBehindMountPointNegativeTest() {
        when(mockMountPointService.getMountPoint(YangInstanceIdentifier.empty()))
                .thenReturn(Optional.of(mockMountPoint));
        when(mockMountPointSchemaService.getGlobalContext()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> parserIdentifier.toSchemaExportContextFromIdentifier(
                "/yang-ext:mount/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION));
    }

    /**
     * Test invoke RPC.
     *
     * <p>
     * Verify if RPC schema node was found.
     */
    @Test
    public void invokeRpcTest() {
        final InstanceIdentifierContext<?> result = parserIdentifier.toInstanceIdentifier(INVOKE_RPC);

        // RPC schema node
        final QName rpcQName = result.getSchemaNode().getQName();
        assertEquals("invoke:rpc:module", rpcQName.getModule().getNamespace().toString());
        assertEquals("rpc-test", rpcQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_RPC, SCHEMA_CONTEXT), result.getInstanceIdentifier());
        assertNull(result.getMountPoint());
        assertEquals(SCHEMA_CONTEXT, result.getSchemaContext());
    }

    /**
     * Test invoke RPC on mount point.
     *
     * <p>
     * Verify if RPC schema node was found.
     */
    @Test
    public void invokeRpcOnMountPointTest() {
        final InstanceIdentifierContext<?> result = parserIdentifier.toInstanceIdentifier(
                MOUNT_POINT_IDENT + "/" + INVOKE_RPC);

        // RPC schema node
        final QName rpcQName = result.getSchemaNode().getQName();
        assertEquals("invoke:rpc:module", rpcQName.getModule().getNamespace().toString());
        assertEquals("rpc-test", rpcQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_RPC, SCHEMA_CONTEXT), result.getInstanceIdentifier());
        assertEquals(mockMountPoint, result.getMountPoint());
        assertEquals(SCHEMA_CONTEXT_ON_MOUNT_POINT, result.getSchemaContext());
    }

    /**
     * Test Action.
     * Verify if Action schema node was found.
     */
    @Test
    public void invokeActionTest() {
        final InstanceIdentifierContext<?> result = parserIdentifier.toInstanceIdentifier(INVOKE_ACTION);

        // Action schema node
        final QName actionQName = result.getSchemaNode().getQName();
        assertEquals("https://example.com/ns/example-actions", actionQName.getModule().getNamespace().toString());
        assertEquals("reset", actionQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_ACTION, SCHEMA_CONTEXT), result.getInstanceIdentifier());
        assertNull(result.getMountPoint());
        assertSame(SCHEMA_CONTEXT, result.getSchemaContext());
    }

    /**
     * Test invoke Action on mount point.
     * Verify if Action schema node was found.
     */
    @Test
    public void invokeActionOnMountPointTest() {
        final InstanceIdentifierContext<?> result = parserIdentifier.toInstanceIdentifier(
                MOUNT_POINT_IDENT + "/" + INVOKE_ACTION);

        // Action schema node
        final QName actionQName = result.getSchemaNode().getQName();
        assertEquals("https://example.com/ns/example-actions", actionQName.getModule().getNamespace().toString());
        assertEquals("reset", actionQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_ACTION, SCHEMA_CONTEXT), result.getInstanceIdentifier());
        assertEquals(mockMountPoint, result.getMountPoint());
        assertEquals(SCHEMA_CONTEXT_ON_MOUNT_POINT, result.getSchemaContext());
    }
}
