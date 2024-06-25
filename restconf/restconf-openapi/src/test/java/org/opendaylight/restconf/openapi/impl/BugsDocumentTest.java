/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;

class BugsDocumentTest extends AbstractDocumentTest {
    /**
     * Model netconf-1309 is used for test issue NETCONF-1309.
     */
    private static final String NETCONF_1309 = "netconf-1309";
    private static final String NETCONF_1309_REVISION = "2024-05-13";

    @BeforeAll
    static void beforeAll() {
        initializeClass("/bugs/");
    }

    /**
     * Tests the swagger document that is result of the call to the '/moduleName' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    void getDocByModuleTest(final String moduleName, final String revision, final String jsonPath)
            throws Exception {
        final var expectedJson = getExpectedDoc("bugs-document/" + jsonPath);
        final var moduleDoc = getDocByModule(moduleName, revision);
        JSONAssert.assertEquals(expectedJson, moduleDoc, IGNORE_ORDER);
    }

    private static Stream<Arguments> getDocByModuleTest() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(NETCONF_1309, NETCONF_1309_REVISION, "controller-netconf-1309.json")
        );
    }
}
