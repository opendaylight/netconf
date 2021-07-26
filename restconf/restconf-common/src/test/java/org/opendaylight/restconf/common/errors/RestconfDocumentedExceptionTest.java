/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.errors;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendaylight.yangtools.yang.common.ErrorTag;

public class RestconfDocumentedExceptionTest {
    @Test
    public void testStatusOf() {
        // TOODO: run this as parameterized test?
        assertStatus("in-use", 409);
        assertStatus("invalid-value", 400);
        assertStatus("too-big", 413);
        assertStatus("missing-attribute", 400);
        assertStatus("bad-attribute", 400);
        assertStatus("unknown-attribute", 400);
        assertStatus("missing-element", 400);
        assertStatus("bad-element", 400);
        assertStatus("unknown-element", 400);
        assertStatus("unknown-namespace", 400);
        assertStatus("access-denied", 403);
        assertStatus("lock-denied", 409);
        assertStatus("resource-denied", 409);
        assertStatus("rollback-failed", 500);
        assertStatus("data-exists", 409);
        assertStatus("data-missing", 409);
        assertStatus("operation-not-supported", 501);
        assertStatus("operation-failed", 500);
        assertStatus("partial-operation", 500);
        assertStatus("malformed-message", 400);
        assertStatus("resource-denied-transport", 503);
    }

    private static void assertStatus(final String tagName, final int status) {
        assertEquals(status, RestconfDocumentedException.statusOf(new ErrorTag(tagName)));
    }
}
