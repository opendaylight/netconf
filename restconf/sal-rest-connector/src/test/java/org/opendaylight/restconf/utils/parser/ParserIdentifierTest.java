/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests fro {@link ParserIdentifier}
 */
public class ParserIdentifierTest {
    private static final String MOUNT_POINT_IDENTIFIER =
            "/mount-point:mount-container" + "/" + RestconfConstants.MOUNT;

    private static final String TEST_IDENTIFIER = "/parser-identifier:cont1/cont2/list2=name/list-in-grouping=name";
    private static final String TEST_IDENTIFIER_MOUNT_POINT = MOUNT_POINT_IDENTIFIER + TEST_IDENTIFIER;

    private static final String TEST_IDENTIFIER_OTHER_MODULES =
            "/parser-identifier:cont3/parser-identifier-included:list-1=name";
    private static final String TEST_IDENTIFIER_OTHER_MODULES_MOUNT_POINT =
            MOUNT_POINT_IDENTIFIER + TEST_IDENTIFIER_OTHER_MODULES;

    private SchemaContext schemaContext;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        schemaContext = TestRestconfUtils.loadSchemaContext("/parser-identifier");
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when all nodes are defined
     * in one module.
     */
    @Test
    public void toInstanceIdentifierTest() {
        InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(TEST_IDENTIFIER, schemaContext);

        assertEquals("/(parser:identifier?revision=2016-06-02)cont1/cont2/list2/" +
                "list2[{(parser:identifier?revision=2016-06-02)name=name}]/list-in-grouping/" +
                "list-in-grouping[{(parser:identifier?revision=2016-06-02)name=name}]",
                context.getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when nodes are defined in
     * multiple modules.
     */
    @Test
    public void toInstanceIdentifierOtherModulesTest() {
        InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(TEST_IDENTIFIER_OTHER_MODULES,
                schemaContext);

        assertEquals("/(parser:identifier?revision=2016-06-02)cont3/list-1/list-1[{(parser:identifier?revision=2016-06-02)name=name}]",
                context.getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier containing mount point when
     * all nodes are defined in one module.
     */
    @Test
    public void toInstanceIdentifierMountPointTest() {
        InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(TEST_IDENTIFIER_MOUNT_POINT,
                schemaContext);
        assertEquals(
                YangInstanceIdentifier.of(QName.create("mount:point", "2016-06-02", "mount-container")),
                context.getInstanceIdentifier());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier containing mount point when
     * nodes are defined in multiple modules.
     */
    @Test
    public void toInstanceIdentifierOtherModulesMountPointTest() {
        InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier(TEST_IDENTIFIER_MOUNT_POINT,
                schemaContext);
        assertEquals(
                YangInstanceIdentifier.of(QName.create("mount:point", "2016-06-02", "mount-container")),
                context.getInstanceIdentifier());
    }

    /** -- negative tests
     */

    /**
     * Negative test when identifier is <code>null</code>. Test fails expecting <code>NullPointerException</code>.
     */
    @Test
    public void toInstanceIdentifierNullIdentifierNegativeTest() {
        thrown.expect(NullPointerException.class);
        ParserIdentifier.toInstanceIdentifier(null, schemaContext);
    }

    /**
     * Negative test when <code>SchemaContext</code> is <code>null</code>. Test fails expecting
     * <code>NullPointerException</code>.
     */
    @Test
    public void toInstanceIdentifierNullSchemaContextNegativeTest() {
        thrown.expect(NullPointerException.class);
        ParserIdentifier.toInstanceIdentifier(TEST_IDENTIFIER, null);
    }

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but does not contain identifier of
     * mount point. Test should fail with <code>IllegalArgumentException</code>.
     *
     * identifier - starts with 'MOUNT'
     */
    @Test
    public void toInstanceIdentifierMissingMountPointNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        ParserIdentifier.toInstanceIdentifier(RestconfConstants.MOUNT + TEST_IDENTIFIER, schemaContext);
    }

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but identifier of mount point is empty.
     * Test should fail with <code>IllegalArgumentException</code>.
     *
     * identifier - starts with '/MOUNT'
     */
    @Test
    public void toInstanceIdentifierEmptyMountPointNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        ParserIdentifier.toInstanceIdentifier("/" + RestconfConstants.MOUNT + TEST_IDENTIFIER, schemaContext);
    }

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but identifier of mount point is empty.
     * Test should fail with <code>IllegalArgumentException</code>.
     *
     * identifier - starts with ' //MOUNT'
     */
    @Test
    public void toInstanceIdentifierEmptyMountPointPathNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        ParserIdentifier.toInstanceIdentifier("//" + RestconfConstants.MOUNT + TEST_IDENTIFIER, schemaContext);
    }

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but identifier of mount point is not
     * valid. Test should fail with <code>IllegalArgumentException</code>.
     */
    @Test
    public void toInstanceIdentifierInvalidMountPointNegativeTest() {
        // todo
    }

    /**
     * Positive test with empty identifier. Root <code>InstanceIdentifierContext</code> should be returned.
     */
    @Test
    public void toInstanceIdentifierEmptyIdentifier() {
        InstanceIdentifierContext<?> context = ParserIdentifier.toInstanceIdentifier("", schemaContext);
        assertEquals(YangInstanceIdentifier.EMPTY, context.getInstanceIdentifier());
    }

    // ---- qname from identifier

    /**
     * Positive test of making <code>QName</code> from identifier and compare values from returned <code>QName</code>
     * to expected values.
     */
    @Test
    public void makeQNameFromIdentifierTest() {
        QName qName = ParserIdentifier.makeQNameFromIdentifier("");

        assertNotNull("QName should be created", qName);
        assertEquals("module1", qName.getLocalName());
        assertEquals("2014-01-01", qName.getFormattedRevision());
    }

    /**
     * Negative test when supplied identifier is in invalid format and then revision is not parsable.
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierNegativeWrongTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier("");
            fail("Test should fail due to invalid identifier format");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test when supplied identifier is too short. <code>RestconfDocumentedException</code> is expected and
     * error type, error tag and error status code are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierNegativeTooShortTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier("");
            fail("Test should fail due to too short identifier format");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Positive test of making <code>QName</code> from identified for module behind mount point. Value from returned
     * <code>QName</code> are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountTest() {
        QName qName = ParserIdentifier.makeQNameFromIdentifier(TEST_IDENTIFIER_MOUNT_POINT);

        assertNotNull("QName should be created", qName);
        assertEquals("module1", qName.getLocalName());
        assertEquals("2014-01-01", qName.getFormattedRevision());
    }

    /**
     * Negative test when supplied identifier for module behind mount point is in invalid format and then revision is not
     * parsable. <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code
     * are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountNegativeInvalidTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier("");
            fail("Test should fail due to invalid identifier format");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test when supplied identifier for module behind mount point is too short.
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code
     * are compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierMountNegativeTooShortTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier("");
            fail("Test shoul fail due to too short identifier format");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    // -- new tests
    /**
     * Negative test trying to make <code>QName</code> from <code>null</code> identifier. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void makeQNameFromIdentifierNullIdentifierNegativeTest() {}

    /**
     * Negative test trying to make <code>QName</code> from empty identifier. Test is expected to fail with
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code is compared to expected
     * values.
     */
    @Ignore
    @Test
    public void makeQNameFromIdentifierEmptyIdentifierNegativeTest() {}

    /**
     * Negative test trying to make <code>QName</code> from mount point identifier when mount point identifier is not
     * at the beginning of the identifier. <code>RestconfDocumentedException</code> is expected and error type, error tag
     * and error status code are compared to expected values.
     */
    @Ignore
    @Test
    public void makeQNameFromIdentifierWrongMountPointPositionNegativeTest() {}

    /**
     * Positive test with identifier containing double slash. Test is expected to pass because of omitting empty
     * strings.
     */
    @Ignore
    @Test
    public void makeQNameFromIdentifierDoubleSlashTest() {}

    // -- to schema export context

    @Test
    public void toSchemaExportContextFromIdentifierTest() {
        SchemaExportContext exportContext = ParserIdentifier.
                toSchemaExportContextFromIdentifier(schemaContext, TEST_IDENTIFIER, null);
        assertNotNull("Export context should be parsed", exportContext);

        Module module = exportContext.getModule();
        assertNotNull("Export context should contains test module", module);

        assertEquals("module1", module.getName());
        assertEquals("2014-01-01", SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("module:1", module.getNamespace().toString());
    }

    /**
     * Negative test trying to get <code>SchemaExportContext</code> with wrong identifier. Test is expected to fail
     * with <code>RestconfDocumentedException</code> and error message is compared to expected value.
     */
    @Test
    public void toSchemaExportContextFromIdentifierNegativeTest() {
        try {
            ParserIdentifier.toSchemaExportContextFromIdentifier(schemaContext, "", null);
            fail("Test should fail due to invalid identifier supplied");
        } catch (RestconfDocumentedException e) {
            assertTrue(e.getMessage().contains("Supplied revision is not in expected date format YYYY-mm-dd"));
        }
    }

    /**
     * Positive test of getting <code>SchemaExportContext</code> when module identified by identifier behind mount point
     * is found and verified.
     */
    @Ignore
    @Test
    public void toSchemaExportContextFromIdentifierMountTest() {}

    /**
     * Negative test of getting <code>SchemaExportContext</code> when module identified by identifier is not found
     * behind mount point. <code>RestconfDocumentedException</code> is expected and error type, error tag and error
     * status code are compared to expected values.
     */
    @Ignore
    @Test
    public void toSchemaExportContextFromIdentifierMountNegativeTest() {}

    // -- new tests

    /**
     * Negative test with identified beginning with slash defining module behind mount point. Test is expected to
     * fail with <code>IllegalArgumentException</code>.
     */
    @Ignore
    @Test
    public void toSchemaExportContextFromIdentifierModuleBehindMountPointBeginsWithSlash() {}

    /**
     * Negative test when supplied identifier is null. <code>NullPointerException</code> is expected.
     */
    @Ignore
    @Test
    public void toSchemaExportContextFromIdentifierNullIdentifierNegativeTest() {}

    /**
     * Negative test when supplied <code>SchemaContext</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void toSchemaExportContextFromIdentifierNullSchemContextNegativeTest() {}

    /**
     * Negative test when supplied <code>SchemaContext</code> is <code>null</code> and identifier specifies module
     * behind mount point. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void toSchemaExportContextFromIdentifierNullSchemContextMountPointNegativeTest() {}

    /**
     * Negative test when supplied <code>DOMMountPointService</code> is <code>null</code> and identifier specifies
     * module behind mount point. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void toSchemaExportContextFromIdentifierNullMountPointService() {}
}
