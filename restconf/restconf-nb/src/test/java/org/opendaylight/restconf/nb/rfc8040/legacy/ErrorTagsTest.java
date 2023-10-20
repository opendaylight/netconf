/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.yangtools.yang.common.ErrorTag;

class ErrorTagsTest {
    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource
    void testStatusOf(final String tagName, final int status) {
        assertEquals(status, ErrorTags.statusOf(new ErrorTag(tagName)).getStatusCode());
    }

    static List<Arguments> testStatusOf() {
        return List.of(
            Arguments.of("in-use", 409),
            Arguments.of("invalid-value", 400),
            Arguments.of("too-big", 413),
            Arguments.of("missing-attribute", 400),
            Arguments.of("bad-attribute", 400),
            Arguments.of("unknown-attribute", 400),
            Arguments.of("missing-element", 400),
            Arguments.of("bad-element", 400),
            Arguments.of("unknown-element", 400),
            Arguments.of("unknown-namespace", 400),
            Arguments.of("access-denied", 403),
            Arguments.of("lock-denied", 409),
            Arguments.of("resource-denied", 409),
            Arguments.of("rollback-failed", 500),
            Arguments.of("data-exists", 409),
            Arguments.of("data-missing", 409),
            Arguments.of("operation-not-supported", 501),
            Arguments.of("operation-failed", 500),
            Arguments.of("partial-operation", 500),
            Arguments.of("malformed-message", 400),
            Arguments.of("resource-denied-transport", 503));
    }
}
