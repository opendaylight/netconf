/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.yangtools.yang.common.ErrorTag;

public class ErrorTagsTest {
    @MethodSource("testStatusOfData")
    @ParameterizedTest(name = "{0} => {1}")
    void testStatusOf(final String tagName, final int status) {
        assertEquals(status, ErrorTags.statusOf(new ErrorTag(tagName)).getStatusCode());
    }

    static Stream<Object[]> testStatusOfData() {
        return Stream.of(new Object[][] {
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

}
