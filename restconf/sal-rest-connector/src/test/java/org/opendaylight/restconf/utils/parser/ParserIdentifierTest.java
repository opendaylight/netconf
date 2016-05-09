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
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * Unit tests fro {@link ParserIdentifier}
 */
public class ParserIdentifierTest {
    private static final String MOUNT_POINT = "mount:point" + "/" + RestconfConstants.MOUNT + "/";

    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String TEST_MODULE_MOUNT = MOUNT_POINT + TEST_MODULE;

    private static final String INVALID_IDENTIFIER = "2014-01-01/module1";
    private static final String INVALID_IDENTIFIER_MOUNT = MOUNT_POINT + INVALID_IDENTIFIER;

    private static final String TOO_SHORT_IDENTIFIER = "module1";
    private static final String TOO_SHORT_IDENTIFIER_MOUNT = MOUNT_POINT + "module1";

    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;

    private SchemaContext schemaContext;
    private SchemaContext schemaContextBehindMountPoint;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        schemaContextBehindMountPoint = TestRestconfUtils.loadSchemaContext("/modules/modules-behind-mount-point");

        when(mountPointService.getMountPoint(any(YangInstanceIdentifier.class))).thenReturn(Optional.of(mountPoint));
        when(mountPoint.getSchemaContext()).thenReturn(schemaContextBehindMountPoint);
    }

    /**
     * Positive test of making <code>InstanceIdentifierContext</code> from identifier.
     */
    @Ignore
    @Test
    public void toInstanceIdentifierTest() {}

    /**
     * Positive test of making <code>InstanceIdentifierContext</code> from identifier containing mount point.
     */
    @Ignore
    @Test
    public void toInstanceIdentifierMountPointTest() {}

    /** -- negative tests
     */

    /**
     * Negative test when identifier is <code>null</code>. Test fails catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void toInstanceIdentifierNullIdentifierNegativeTest() {}

    /**
     * Negative test when <code>SchemaContext</code> is <code>null</code>. Test fails catching
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void toInstanceIdentifierNullSchemaContextNegativeTest() {}

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but does not contain identifier of
     * mount point. Test should fail with <code>IllegalArgumentException</code>.
     *
     * identifier - starts with 'MOUNT'
     */
    @Ignore
    @Test
    public void toInstanceIdentifierMissingMountPointNegativeTest() {}

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but identifier of mount point is empty.
     * Test should fail with <code>IllegalArgumentException</code>.
     *
     * identifier - starts with '/MOUNT'
     */
    @Ignore
    @Test
    public void toInstanceIdentifierEmptyMountPointNegativeTest() {}

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but identifier of mount point is empty.
     * Test should fail with <code>IllegalArgumentException</code>.
     *
     * identifier - starts with ' //MOUNT'
     */
    @Ignore
    @Test
    public void toInstanceIdentifierEmptyMountPointPathNegativeTest() {}

    /**
     * Negative test when identifier contains {@link RestconfConstants#MOUNT} but identifier of mount point is not
     * valid. Test should fail with <code>IllegalArgumentException</code>.
     */
    @Ignore
    @Test
    public void toInstanceIdentifierInvalidMountPointNegativeTest() {}

    /**
     * Test with empty identifier. Root <code>InstanceIdentifierContext</code> should be returned.
     */
    @Ignore
    @Test
    public void toInstanceIdentifierEmptyIdentifier() {}

    // ---- qname from identifier

    /**
     * Positive test of making <code>QName</code> from identifier and compare values from returned <code>QName</code>
     * to expected values.
     */
    @Test
    public void makeQNameFromIdentifierTest() {
        QName qName = ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE);

        assertNotNull("QName should be created", qName);
        assertEquals("module1", qName.getLocalName());
        assertEquals("2014-01-01", qName.getFormattedRevision());
    }

    /**
     * Negative test when supplied identified is in invalid format and then revision is not parsable.
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void makeQNameFromIdentifierNegativeWrongTest() {
        try {
            ParserIdentifier.makeQNameFromIdentifier(INVALID_IDENTIFIER);
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
            ParserIdentifier.makeQNameFromIdentifier(TOO_SHORT_IDENTIFIER);
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
        QName qName = ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_MOUNT);

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
            ParserIdentifier.makeQNameFromIdentifier(INVALID_IDENTIFIER_MOUNT);
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
            ParserIdentifier.makeQNameFromIdentifier(TOO_SHORT_IDENTIFIER_MOUNT);
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
                toSchemaExportContextFromIdentifier(schemaContext, TEST_MODULE, null);
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
            ParserIdentifier.toSchemaExportContextFromIdentifier(schemaContext, INVALID_IDENTIFIER, null);
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
