/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opendaylight.yangtools.yang.common.ErrorTag;

@RunWith(Parameterized.class)
public class ErrorTagsTest {
    @Parameters(name = "{0} => {1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "in-use", 409 },
            { "invalid-value", 400 },
            { "too-big", 413 },
            { "missing-attribute", 400 },
            { "bad-attribute", 400 },
            { "unknown-attribute", 400 },
            { "missing-element", 400 },
            { "bad-element", 400 },
            { "unknown-element", 400 },
            { "unknown-namespace", 400 },
            { "access-denied", 403 },
            { "lock-denied", 409 },
            { "resource-denied", 409 },
            { "rollback-failed", 500 },
            { "data-exists", 409 },
            { "data-missing", 409 },
            { "operation-not-supported", 501 },
            { "operation-failed", 500 },
            { "partial-operation", 500 },
            { "malformed-message", 400 },
            { "resource-denied-transport", 503 }
        });
    }

    private final String tagName;
    private final int status;

    public ErrorTagsTest(final String tagName, final int status) {
        this.tagName = requireNonNull(tagName);
        this.status = status;
    }

    @Test
    public void testStatusOf() {
        assertEquals(status, ErrorTags.statusOf(new ErrorTag(tagName)).getStatusCode());
    }
}
