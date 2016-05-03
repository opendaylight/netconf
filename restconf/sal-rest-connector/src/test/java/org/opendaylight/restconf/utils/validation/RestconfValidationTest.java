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

public class RestconfValidationTest {
    private static final List<String> revisions = Arrays.asList("2014-01-01", "2015-01-01", "2016-01-01");
    private static final List<String> names = Arrays.asList("module1", "module2", "module3");

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

    @Test
    public void validateAndGetRevisionNotSuppliedTest() {
        try {
            RestconfValidation.validateAndGetRevision(new ArrayList<String>().iterator());
            fail("Test should fail due to not supplied module revision");
        } catch (RestconfDocumentedException e) {
            assertEquals(400, e.getStatus().getStatusCode());
        }
    }

    @Test
    public void validateAndGetRevisionNotParsableTest() {
        try {
            RestconfValidation.validateAndGetRevision(Arrays.asList("not-parsable-as-date").iterator());
            fail("Tets should fail due to not parsable module revision");
        } catch (RestconfDocumentedException e) {
            assertTrue(e.getMessage().startsWith("Supplied revision is not in expected date format YYYY-mm-dd"));
        }
    }

    @Test
    public void validateAndGetModulNameTest() {
        String moduleName = RestconfValidation.validateAndGetModulName(names.iterator());
        assertNotNull(moduleName);
        assertEquals("Correct module name should be validated", "module1", moduleName);
    }

    @Test
    public void validateAndGetModulNameNotSuppliedTest() {
        try {
            RestconfValidation.validateAndGetModulName(new ArrayList<String>().iterator());
            fail("Test should fasil due to not supplied module name");
        } catch (RestconfDocumentedException e) {
            assertEquals(400, e.getStatus().getStatusCode());
        }
    }
}
