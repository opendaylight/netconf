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
    /**
     * Test for <a href="https://lf-opendaylight.atlassian.net/browse/NETCONF-1389">NETCONF-1389</a>.
     */
    private static final String NETCONF_1389 = "netconf-1389";
    private static final String NETCONF_1389_REVISION = "2024-09-11";

    @BeforeAll
    static void beforeAll() {
        initializeClass("/bugs/");
    }

    /**
     * Tests the swagger document generated for the model associated with the given {@code bugName}.
     */
    @ParameterizedTest
    @MethodSource
    void getDocByModuleTest(final String bugName, final String revision) throws Exception {
        final var expectedJson = getExpectedDoc("bugs-document/" + bugName + ".json");
        final var moduleDoc = getDocByModule(bugName, revision);
        JSONAssert.assertEquals(expectedJson, moduleDoc, IGNORE_ORDER);
    }

    private static Stream<Arguments> getDocByModuleTest() {
        // bugName, revision
        return Stream.of(
            Arguments.of(NETCONF_1309, NETCONF_1309_REVISION),
            Arguments.of(NETCONF_1389, NETCONF_1389_REVISION)
        );
    }
}
