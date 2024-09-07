/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opendaylight.restconf.server.PathParameters.DATA;
import static org.opendaylight.restconf.server.PathParameters.MODULES;
import static org.opendaylight.restconf.server.PathParameters.OPERATIONS;
import static org.opendaylight.restconf.server.PathParameters.YANG_LIBRARY_VERSION;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PathParametersTest {
    private static final String BASE = "/base";
    private static final String BASE1 = "/base/";
    private static final String UNSUPPORTED_BASE = "/basex";
    private static final String UNSUPPORTED_RESOURCE = "/resource";
    private static final String CHILD_ID = "child/identifier/id=1";
    private static final String CHILD_PATH = "/" + CHILD_ID;

    @ParameterizedTest
    @MethodSource
    void pathParameters(final String fullPath, final String resource, final String childId) {
        final var pathParams = PathParameters.from(fullPath, BASE);
        assertNotNull(pathParams);
        assertEquals(resource, pathParams.apiResource());
        assertEquals(childId, pathParams.childIdentifier());
    }

    static Stream<Arguments> pathParameters() {
        return Stream.of(
            Arguments.of(BASE, "", ""),
            Arguments.of(BASE1, "", ""),
            Arguments.of(BASE + DATA, DATA, ""),
            Arguments.of(BASE + DATA + "/", DATA, ""),
            Arguments.of(BASE + DATA + CHILD_PATH, DATA, CHILD_ID),
            Arguments.of(BASE + OPERATIONS, OPERATIONS, ""),
            Arguments.of(BASE + OPERATIONS + CHILD_PATH, OPERATIONS, CHILD_ID),
            Arguments.of(BASE + YANG_LIBRARY_VERSION, YANG_LIBRARY_VERSION, ""),
            Arguments.of(BASE + MODULES, MODULES, ""),
            Arguments.of(BASE + MODULES + CHILD_PATH, MODULES, CHILD_ID),

            // unsupported
            Arguments.of(UNSUPPORTED_BASE, "", ""),
            Arguments.of(UNSUPPORTED_BASE + DATA, "", ""),
            Arguments.of(BASE + UNSUPPORTED_RESOURCE, "", "")
        );
    }
}
