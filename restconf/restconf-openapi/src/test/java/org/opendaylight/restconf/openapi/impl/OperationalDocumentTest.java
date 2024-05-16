/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;

public class OperationalDocumentTest extends AbstractDocumentTest {
    /**
     * Model action-types is used for test correct generating of action statements for openapi.
     */
    private static final String ACTION_TYPES = "action-types";
    /**
     * Model operational is used for test correct generating of operational parameters for openapi.
     */
    private static final String OPERATIONAL = "operational";

    @BeforeAll
    public static void beforeAll() {
        initializeClass("/operational/");
    }

    /**
     * Tests the swagger document that is result of the call to the '/single' endpoint.
     */
    @Test
    public void getAllModulesDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("operational-document/controller-all.json");
        final var allModulesDoc = getAllModulesDoc();
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/moduleName' endpoint.
     */
    @ParameterizedTest
    @MethodSource("getOperationalParameters")
    public void getDocByModuleTest(final String moduleName, final String revision, final String jsonPath)
            throws Exception {
        final var expectedJson = getExpectedDoc("operational-document/" + jsonPath);
        final var moduleDoc = getDocByModule(moduleName, revision);
        JSONAssert.assertEquals(expectedJson, moduleDoc, IGNORE_ORDER);
    }

    static Stream<Arguments> getOperationalParameters() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(ACTION_TYPES, null, "controller-action-types.json"),
            Arguments.of(OPERATIONAL, null, "controller-operational.json")
        );
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1' endpoint.
     */
    @Test
    public void getMountDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("operational-document/device-all.json");
        final var allModulesDoc = getMountDoc();
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/moduleName' endpoint.
     */
    @ParameterizedTest
    @MethodSource("getOperationalMountParameters")
    public void getMountDocByModuleTest(final String moduleName, final String revision, final String jsonPath)
            throws Exception {
        final var expectedJson = getExpectedDoc("operational-document/" + jsonPath);
        final var moduleDoc = getMountDocByModule(moduleName, revision);
        JSONAssert.assertEquals(expectedJson, moduleDoc, IGNORE_ORDER);
    }

    static Stream<Arguments> getOperationalMountParameters() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(ACTION_TYPES, null, "device-action-types.json"),
            Arguments.of(OPERATIONAL, null, "device-operational.json")
        );
    }
}
