/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PathParametersTest {
    private static final String BASE = "/base";
    private static final String BASE1 = "/base/";
    private static final String BASEX = "/basex";
    private static final String RESOURCE = "/resource";
    private static final String RESOURCE1 = "/resource/";
    private static final String PATH_ARG = "/ARG/WITH/SLASHES";
    private static final String ARG = "ARG/WITH/SLASHES";

    @ParameterizedTest
    @MethodSource
    void pathParameters(final String fullPath, final String resource, final String argument) {
        final var pathParams = PathParameters.from(fullPath, BASE);
        assertNotNull(pathParams);
        assertEquals(resource, pathParams.apiResource());
    }

    static Stream<Arguments> pathParameters() {
        return Stream.of(
            Arguments.of(BASE, "", ""),
            Arguments.of(BASE1, "", ""),
            Arguments.of(BASE + RESOURCE, RESOURCE, ""),
            Arguments.of(BASE + RESOURCE1, RESOURCE, ""),
            Arguments.of(BASE + RESOURCE + PATH_ARG, RESOURCE, ARG),
            // base mismatch
            Arguments.of(BASEX, "", ""),
            Arguments.of(BASEX + RESOURCE, "", ""),
            Arguments.of(BASEX + RESOURCE + PATH_ARG, "", "")
        );
    }
}
