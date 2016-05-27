/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;

/**
 * Unit test for {@link RestconfValidation}
 */
public class RestconfValidationTest {
    private static final List<String> revisions = Arrays.asList("2014-01-01", "2015-01-01", "2016-01-01");
    private static final List<String> names = Arrays.asList("_module-1", "_module-2", "_module-3");

    /**
     * Test of successful validation of module revision.
     */
    @Test
    public void validateAndGetRevisionTest() {
        Date revision = RestconfValidation.validateAndGetRevision(revisions.iterator());
        assertNotNull("Correct module revision should be validated", revision);

        Calendar c = Calendar.getInstance();
        c.setTime(revision);

        assertEquals(2014, c.get(Calendar.YEAR));
        assertEquals(0, c.get(Calendar.MONTH));
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Negative test of module revision validation when there is no revision. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetRevisionNotSuppliedTest() {
        try {
            RestconfValidation.validateAndGetRevision(new ArrayList<String>().iterator());
            fail("Test should fail due to not supplied module revision");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of module revision validation when supplied revision is not parsable as revision. Test fails
     * catching <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validateAndGetRevisionNotParsableTest() {
        try {
            RestconfValidation.validateAndGetRevision(Arrays.asList("not-parsable-as-date").iterator());
            fail("Test should fail due to not parsable module revision");
        } catch (RestconfDocumentedException e) {
            assertTrue(e.getMessage().contains("Supplied revision is not in expected date format YYYY-mm-dd"));
        }
    }

    /**
     * Test of successful validation of module name.
     */
    @Test
    public void validateAndGetModulNameTest() {
        String moduleName = RestconfValidation.validateAndGetModulName(names.iterator());
        assertNotNull("Correct module name should be validated", moduleName);
        assertEquals("_module-1", moduleName);
    }

    /**
     * Negative test of module name validation when there is no module name. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetModulNameNotSuppliedTest() {
        try {
            RestconfValidation.validateAndGetModulName(new ArrayList<String>().iterator());
            fail("Test should fail due to not supplied module name");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of module name validation when supplied name is not parsable as module name on the first
     * character. Test fails catching <code>RestconfDocumentedException</code> and checking for correct error type,
     * error tag and error status code.
     */
    @Test
    public void validateAndGetModuleNameNotParsableFirstTest() {
       try {
           RestconfValidation.validateAndGetModulName(
                   Arrays.asList("01-not-parsable-as-name-on-firts-char").iterator());
           fail("Test should fail due to not parsable module name on the first character");
       } catch (RestconfDocumentedException e) {
           assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
           assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
           assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
       }
    }

    /**
     * Negative test of module name validation when supplied name is not parsable as module name on any of the
     * characters after the first character. Test fails catching <code>RestconfDocumentedException</code> and checking
     * for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetModuleNameNotParsableNextTest() {
        try {
            RestconfValidation.validateAndGetModulName(
                    Arrays.asList("not-parsable-as-name-after-first-char*").iterator());
            fail("Test should fail due to not parsable module name on any character after the first character");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of module name validation when supplied name begins with 'XML' ignore case. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetModuleNameNotParsableXmlTest() {
        try {
            RestconfValidation.validateAndGetModulName(Arrays.asList("xMl-module-name").iterator());
            fail("Test should fail due to module name beginning with 'xMl'");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals(400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }
}
