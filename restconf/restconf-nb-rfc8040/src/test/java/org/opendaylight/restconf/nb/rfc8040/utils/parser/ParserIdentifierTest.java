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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.Maps;
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
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link ParserIdentifier}.
 */
public class ParserIdentifierTest {
    // mount point identifier
    private static final String MOUNT_POINT_IDENT =
            "mount-point:mount-container/point-number" + "/" + RestconfConstants.MOUNT;

    // invalid mount point identifier
    private static final String INVALID_MOUNT_POINT_IDENT =
            "mount-point:point-number" + "/" + RestconfConstants.MOUNT;

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

    // schema context with test modules
    private SchemaContext schemaContext;
    // contains the same modules but it is different object (it can be compared with equals)
    private SchemaContext schemaContextOnMountPoint;

    private static final String TEST_MODULE_NAME = "test-module";
    private static final String TEST_MODULE_REVISION = "2016-06-02";
    private static final String TEST_MODULE_NAMESPACE = "test:module";

    private static final String INVOKE_RPC = "invoke-rpc-module:rpc-test";

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

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.schemaContext = YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles("/parser-identifier"));
        this.schemaContextOnMountPoint =
                YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles("/parser-identifier"));

        // create and register mount point
        this.mountPoint = SimpleDOMMountPoint.create(
                YangInstanceIdentifier.builder()
                        .node(QName.create("mount:point", "2016-06-02", "mount-container"))
                        .node(QName.create("mount:point", "2016-06-02", "point-number"))
                        .build(),
                ImmutableClassToInstanceMap.copyOf(Maps.newHashMap()),
                this.schemaContextOnMountPoint
        );

        this.mountPointService = new DOMMountPointServiceImpl();
        ((DOMMountPointServiceImpl) this.mountPointService).registerMountPoint(this.mountPoint);

        // register mount point with null schema context
        when(this.mockMountPoint.getSchemaContext()).thenReturn(null);
        when(this.mockMountPointService.getMountPoint(YangInstanceIdentifier.EMPTY))
                .thenReturn(Optional.of(this.mockMountPoint));
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
        final InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(
                TEST_IDENT, this.schemaContext, Optional.absent());

        assertEquals("Returned not expected identifier",
                TEST_IDENT_RESULT, context .getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when nodes are defined in
     * multiple modules.
     */
    @Test
    public void toInstanceIdentifierOtherModulesTest() {
        final InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(
                TEST_IDENT_OTHERS, this.schemaContext, Optional.absent());

        assertEquals("Returned not expected identifier",
                TEST_IDENT_OTHERS_RESULT, context.getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier containing
     * {@link RestconfConstants#MOUNT}.
     */
    @Test
    public void toInstanceIdentifierMountPointTest() {
        final InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(
                MOUNT_POINT_IDENT + "/" + TEST_IDENT, this.schemaContext, Optional.of(this.mountPointService));

        assertEquals("Returned not expected identifier",
                TEST_IDENT_RESULT.toString(), context.getInstanceIdentifier().toString());

        assertEquals("Mount point not found",
                this.mountPoint, context.getMountPoint());

        assertEquals("Schema context from mount point expected",
                this.schemaContextOnMountPoint, context.getSchemaContext());
    }

    /**
     * Test of creating <code>InstanceIdentifierContext</code> when identifier is <code>null</code>.
     * <code>{@link YangInstanceIdentifier#EMPTY}</code> should be returned.
     */
    @Test
    public void toInstanceIdentifierNullIdentifierTest() {
        final InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(
                null, this.schemaContext, Optional.absent());
        assertEquals("Returned not expected identifier",
                YangInstanceIdentifier.EMPTY, context.getInstanceIdentifier());
    }

    /**
     * Negative test of creating <code>InstanceIdentifierContext</code> when <code>SchemaContext</code> is
     * <code>null</code>. Test fails expecting <code>NullPointerException</code>.
     */
    @Test
    public void toInstanceIdentifierNullSchemaContextNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        ParserIdentifier.toInstanceIdentifier(TEST_IDENT, null, Optional.absent());
    }

    /**
     * Api path can be empty. <code>YangInstanceIdentifier.EMPTY</code> is expected to be returned.
     */
    @Test
    public void toInstanceIdentifierEmptyIdentifierTest() {
        final InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(
                "", this.schemaContext, Optional.absent());
        assertEquals("Returned not expected identifier",
                YangInstanceIdentifier.EMPTY, context.getInstanceIdentifier());
    }

    /**
     * Negative test with invalid test identifier. Test should fail with <code>IllegalArgumentException</code>.
     */
    @Test
    public void toInstanceIdentifierInvalidIdentifierNegativeTest() {
        this.thrown.expect(IllegalArgumentException.class);
        ParserIdentifier.toInstanceIdentifier(INVALID_TEST_IDENT, this.schemaContext, Optional.absent());
    }

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but identifier part is not valid. Test
     * should fail with <code>IllegalArgumentException</code>.
     */
    @Test
    public void toInstanceIdentifierMountPointInvalidIdentifierNegativeTest() {
        this.thrown.expect(IllegalArgumentException.class);
        ParserIdentifier.toInstanceIdentifier(
                INVALID_MOUNT_POINT_IDENT, this.schemaContext, Optional.of(this.mountPointService));
    }

    /**
     * Negative test when <code>DOMMountPoint</code> cannot be found. Test is expected to fail with
     * <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void toInstanceIdentifierMissingMountPointNegativeTest() {
        try {
            ParserIdentifier.toInstanceIdentifier(
                    "" + "/" + RestconfConstants.MOUNT, this.schemaContext, Optional.of(this.mountPointService));
            fail("Test should fail due to missing mount point");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test when <code>{@link DOMMountPointService}</code> is absent. Test is expected to fail with
     * <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void toInstanceIdentifierMissingMountPointServiceNegativeTest() {
        try {
            ParserIdentifier.toInstanceIdentifier(RestconfConstants.MOUNT, this.schemaContext, Optional.absent());
            fail("Test should fail due to absent mount point service");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    ErrorType.APPLICATION, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    ErrorTag.OPERATION_FAILED, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    500, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
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
        final QName qName = ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION);

        assertNotNull("QName should be created", qName);
        assertEquals("Returned not expected module name",
                TEST_MODULE_NAME, qName.getLocalName());
        assertEquals("Returned not expected module revision",
                TEST_MODULE_REVISION, qName.getFormattedRevision());
    }

    /**
     * Negative test when supplied identifier is in invalid format and then revision is not parsable.
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierInvalidIdentifierNegativeTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME);
            fail("Test should fail due to invalid identifier format");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test when supplied identifier is too short (contains only module name).
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierTooShortIdentifierNegativeTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_NAME);
            fail("Test should fail due to too short identifier format");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Positive test of making <code>QName</code> from identifier for module behind mount point. Value from returned
     * <code>QName</code> are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountTest() {
        final QName qName = ParserIdentifier.makeQNameFromIdentifier(
                MOUNT_POINT_IDENT
                + "/"
                + TEST_MODULE_NAME
                + "/"
                + TEST_MODULE_REVISION);

        assertNotNull("QName should be created", qName);
        assertEquals("Returned not expected module name",
                TEST_MODULE_NAME, qName.getLocalName());
        assertEquals("Returned not expected module revision",
                TEST_MODULE_REVISION, qName.getFormattedRevision());
    }

    /**
     * Negative test when supplied identifier for module behind mount point is in invalid format and then revision is
     * not parsable. <code>RestconfDocumentedException</code> is expected and error type, error tag and error status
     * code are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountPointInvalidIdentifierNegativeTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier(
                    MOUNT_POINT_IDENT
                    + "/"
                    + TEST_MODULE_REVISION
                    + "/"
                    + TEST_MODULE_NAME);

            fail("Test should fail due to invalid identifier format");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test when supplied identifier for module behind mount point is too short (contains only module name).
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code
     * are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountPointTooShortIdentifierNegativeTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier(
                    MOUNT_POINT_IDENT
                    + "/"
                    + TEST_MODULE_NAME);

            fail("Test should fail due to too short identifier format");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test trying to make <code>QName</code> from <code>null</code> identifier. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void makeQNameFromIdentifierNullIdentifierNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        ParserIdentifier.makeQNameFromIdentifier(null);
    }

    /**
     * Negative test trying to make <code>QName</code> from empty identifier. Test is expected to fail with
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code is compared to expected
     * values.
     */
    @Test
    public void makeQNameFromIdentifierEmptyIdentifierNegativeTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier("");
            fail("Test should fail due to empty identifier");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test with identifier containing double slash. Between // there is one empty string which will be
     * incorrectly considered to be module revision. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Test
    public void makeQNameFromIdentifierDoubleSlashNegativeTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_NAME + "//" + TEST_MODULE_REVISION);
            fail("Test should fail due to identifier containing double slash");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
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
        final SchemaExportContext exportContext = ParserIdentifier.toSchemaExportContextFromIdentifier(
                this.schemaContext, TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, null, sourceProvider);

        assertNotNull("Export context should be parsed", exportContext);

        final Module module = exportContext.getModule();
        assertNotNull("Export context should contains test module", module);

        assertEquals("Returned not expected module name",
                TEST_MODULE_NAME, module.getName());
        assertEquals("Returned not expected module revision",
                TEST_MODULE_REVISION, SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("Returned not expected module namespace",
                TEST_MODULE_NAMESPACE, module.getNamespace().toString());
    }

    /**
     * Test of getting <code>SchemaExportContext</code> when desired module is not found.
     * <code>SchemaExportContext</code> should be created but module should be set to <code>null</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNotFoundTest() {
        final SchemaExportContext exportContext = ParserIdentifier.toSchemaExportContextFromIdentifier(
                this.schemaContext,
                "not-existing-module" + "/" + "2016-01-01",
                null, sourceProvider);

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
        try {
            ParserIdentifier.toSchemaExportContextFromIdentifier(
                    this.schemaContext, TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME, null, sourceProvider);
            fail("Test should fail due to invalid identifier supplied");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Positive test of getting <code>SchemaExportContext</code> for module behind mount point.
     * Expected module name, revision and namespace are verified.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointTest() {
        final SchemaExportContext exportContext = ParserIdentifier.toSchemaExportContextFromIdentifier(
                this.schemaContext,
                MOUNT_POINT_IDENT + "/" + TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION,
                this.mountPointService, sourceProvider);

        final Module module = exportContext.getModule();
        assertNotNull("Export context should contains test module", module);

        assertEquals("Returned not expected module name",
                TEST_MODULE_NAME, module.getName());
        assertEquals("Returned not expected module revision",
                TEST_MODULE_REVISION, SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("Returned not expected module namespace",
                TEST_MODULE_NAMESPACE, module.getNamespace().toString());
    }

    /**
     * Negative test of getting <code>SchemaExportContext</code> when desired module is not found behind mount point.
     * <code>SchemaExportContext</code> should be still created but module should be set to <code>null</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointNotFoundTest() {
        final SchemaExportContext exportContext = ParserIdentifier.toSchemaExportContextFromIdentifier(
                this.schemaContext,
                MOUNT_POINT_IDENT + "/" + "not-existing-module" + "/" + "2016-01-01",
                this.mountPointService, sourceProvider);

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
        try {
            ParserIdentifier.toSchemaExportContextFromIdentifier(
                    this.schemaContext,
                    MOUNT_POINT_IDENT + "/" + TEST_MODULE_REVISION + "/" + TEST_MODULE_NAME,
                    this.mountPointService, sourceProvider);

            fail("Test should fail due to invalid identifier supplied");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of getting <code>SchemaExportContext</code> when supplied identifier is null.
     * <code>NullPointerException</code> is expected. <code>DOMMountPointService</code> is not used.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNullIdentifierNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        ParserIdentifier.toSchemaExportContextFromIdentifier(this.schemaContext, null, null, sourceProvider);
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>SchemaContext</code> is
     * <code>null</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNullSchemaContextNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        ParserIdentifier.toSchemaExportContextFromIdentifier(null, TEST_MODULE_NAME + "/" + TEST_MODULE_REVISION, null,
                sourceProvider);
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>SchemaContext</code> is
     * <code>null</code> and identifier specifies module behind mount point. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierMountPointNullSchemaContextNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        ParserIdentifier.toSchemaExportContextFromIdentifier(
                null,
                MOUNT_POINT_IDENT
                + "/"
                + TEST_MODULE_NAME
                + "/"
                + TEST_MODULE_REVISION,
                this.mountPointService,
                sourceProvider);
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when supplied <code>DOMMountPointService</code>
     * is <code>null</code> and identifier defines module behind mount point. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNullMountPointServiceNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        ParserIdentifier.toSchemaExportContextFromIdentifier(
                this.schemaContext,
                MOUNT_POINT_IDENT
                + "/"
                + TEST_MODULE_NAME
                + "/"
                + TEST_MODULE_REVISION,
                null,
                sourceProvider);
    }

    /**
     * Negative test of of getting <code>SchemaExportContext</code> when <code>SchemaContext</code> behind mount
     * point is <code>null</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNullSchemaContextBehindMountPointNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        ParserIdentifier.toSchemaExportContextFromIdentifier(
                this.schemaContext,
                "/"
                + RestconfConstants.MOUNT
                + "/"
                + TEST_MODULE_NAME
                + "/"
                + TEST_MODULE_REVISION,
                this.mockMountPointService,
                sourceProvider);
    }

    /**
     * Test invoke RPC.
     *
     * <p>
     * Verify if RPC schema node was found.
     */
    @Test
    public void invokeRpcTest() {
        final InstanceIdentifierContext<?> result = ParserIdentifier.toInstanceIdentifier(
                INVOKE_RPC, this.schemaContext, Optional.absent());

        // RPC schema node
        final QName rpcQName = result.getSchemaNode().getQName();
        assertEquals("invoke:rpc:module", rpcQName.getModule().getNamespace().toString());
        assertEquals("rpc-test", rpcQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_RPC, schemaContext), result.getInstanceIdentifier());
        assertEquals(null, result.getMountPoint());
        assertEquals(this.schemaContext, result.getSchemaContext());
    }

    /**
     * Test invoke RPC on mount point.
     *
     * <p>
     * Verify if RPC schema node was found.
     */
    @Test
    public void invokeRpcOnMountPointTest() {
        final InstanceIdentifierContext<?> result = ParserIdentifier.toInstanceIdentifier(
                MOUNT_POINT_IDENT + "/" + INVOKE_RPC, this.schemaContext, Optional.of(this.mountPointService));

        // RPC schema node
        final QName rpcQName = result.getSchemaNode().getQName();
        assertEquals("invoke:rpc:module", rpcQName.getModule().getNamespace().toString());
        assertEquals("rpc-test", rpcQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(INVOKE_RPC, schemaContext), result.getInstanceIdentifier());
        assertEquals(this.mountPoint, result.getMountPoint());
        assertEquals(this.schemaContextOnMountPoint, result.getSchemaContext());
    }
}
