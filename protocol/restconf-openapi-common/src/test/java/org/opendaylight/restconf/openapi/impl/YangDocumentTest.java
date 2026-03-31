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

class YangDocumentTest extends AbstractDocumentTest {
    /**
     * Model action-types is used for test correct generating of action statements for openapi.
     */
    private static final String ACTION_TYPES = "action-types";
    /**
     * Model choice-test is used for test correct generation of action statements for openapi.
     */
    private static final String CHOICE_TEST = "choice-test";
    /**
     * Model definition-test is used for test correct generating of definition for nodes and operations for openapi.
     */
    private static final String DEFINITION_TEST = "definition-test";
    /**
     * Model duplication-service is used for augmentation of duplication-service model.
     */
    private static final String DUPLICATION_SERVICE = "duplication-service";
    /**
     * Model duplication-test is used for test if the same schema is not duplicated in openapi.
     */
    private static final String DUPLICATION_TEST = "duplication-test";
    /**
     * Model mandatory-test is used for test correct generating of mandatory nodes for openapi.
     */
    private static final String MANDATORY_TEST = "mandatory-test";
    /**
     * Model my-yang is used for test correct generating of simple openapi object.
     */
    private static final String MY_YANG = "my-yang";
    private static final String MY_YANG_REV = "2022-10-06";
    /**
     * Model opflex defines the group-based policy OpFlex renderer model.
     */
    private static final String OPFLEX = "opflex";
    private static final String OPFLEX_REV = "2014-05-28";
    /**
     * Model path-params-test is used for test correct generating of parameters numbering for openapi.
     */
    private static final String PATH_PARAMS_TEST = "path-params-test";
    /**
     * Model recursive is used for test correct generating of recursive parameters for openapi.
     */
    private static final String RECURSIVE = "recursive";
    private static final String RECURSIVE_REV = "2023-05-22";
    /**
     * Model string-types is used for test correct generating of types with restrictions for openapi.
     */
    private static final String STRING_TYPES = "string-types";
    /**
     * Model strings-from-regex is used for test correct generating of string patterns for openapi.
     */
    private static final String STRINGS_FROM_REGEX = "strings-from-regex";
    /**
     * Model strings-examples-length is used for test correct generating of string patterns with length restriction
     * for openapi.
     */
    private static final String STRING_EXAMPLES_LENGTH = "strings-examples-length";
    /**
     * Model test-container-childs is used for test correct generating of min-elements, max-elements and example
     * elements for openapi.
     */
    private static final String TEST_CONTAINER_CHILDS = "test-container-childs";
    private static final String TEST_CONTAINER_CHILDS_REV = "2023-09-28";
    /**
     * Model toaster is used for test correct generating of complex openapi object.
     */
    private static final String TOASTER = "toaster";
    private static final String TOASTER_REV = "2009-11-20";
    /**
     * Model toaster_augmented is used for test correct generating of augmented model for openapi.
     */
    private static final String TOASTER_AUGMENTED = "toaster-augmented";
    private static final String TOASTER_AUGMENTED_REV = "2014-07-14";
    /**
     * Model toaster_short is used for test correct generating of types with restrictions for openapi.
     */
    private static final String TOASTER_SHORT = "toaster2";
    private static final String TOASTER_SHORT_REV = "2009-11-20";
    /**
     * Model typed-params is used for test correct generating of all built-in types and assignment to allowed
     * types for openapi.
     */
    private static final String TYPED_PARAMS = "typed-params";
    private static final String TYPED_PARAMS_REV = "2023-10-24";

    @BeforeAll
    static void beforeAll() {
        initializeClass("/yang/");
    }

    /**
     * Tests the swagger document that is result of the call to the '/single' endpoint.
     */
    @Test
    void getAllModulesDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/controller-all.json");
        final var allModulesDoc = getAllModulesDoc(0, 0, 0, 0);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/single?width=1' endpoint.
     */
    @Test
    public void getAllModulesDocWidthOneTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/controller-all-width-one.json");
        final var allModulesDoc = getAllModulesDoc(1, 0, 0, 0);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/single?depth=1' endpoint.
     */
    @Test
    public void getAllModulesDocDepthOneTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/controller-all-depth-one.json");
        final var allModulesDoc = getAllModulesDoc(0, 1, 0, 0);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/single?width=1&depth=1' endpoint.
     */
    @Test
    public void getAllModulesDocWidthAndDepthOneTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/controller-all-width-and-depth-one.json");
        final var allModulesDoc = getAllModulesDoc(1, 1, 0, 0);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/single?offset=3&limit=5' endpoint. The offset is
     * set to 3 and the limit is set to 5, so we expect only 5 modules starting from the 4th module. The modules are
     * sorted by name. Expected modules are: duplication-test(index: 4), mandatory-test(index: 5), my-yang(index: 6),
     * path-params-test(index: 8) and recursive(index: 9). For modules duplication-service(index: 3) and
     * opflex(index: 7) we expect that they are not included in the result because they not contain any rpcs of data
     * nodes.
     */
    @Test
    void getLimitedModulesDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/controller-offset3&limit5.json");
        final var allModulesDoc = getAllModulesDoc(0, 0, 3, 5);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/moduleName' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    void getDocByModuleTest(final String moduleName, final String revision, final String jsonPath)
            throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/" + jsonPath);
        final var moduleDoc = getDocByModule(moduleName, revision);
        JSONAssert.assertEquals(expectedJson, moduleDoc, IGNORE_ORDER);
    }

    private static Stream<Arguments> getDocByModuleTest() {
        return Stream.of(
            Arguments.of(ACTION_TYPES, null, "controller-action-types.json"),
            Arguments.of(CHOICE_TEST, null, "controller-choice-test.json"),
            Arguments.of(DEFINITION_TEST, null, "controller-definition-test.json"),
            Arguments.of(DUPLICATION_SERVICE, null, "controller-duplication-service.json"),
            Arguments.of(DUPLICATION_TEST, null, "controller-duplication-test.json"),
            Arguments.of(MANDATORY_TEST, null, "controller-mandatory-test.json"),
            Arguments.of(MY_YANG, MY_YANG_REV, "controller-my-yang.json"),
            Arguments.of(OPFLEX, OPFLEX_REV, "controller-opflex.json"),
            Arguments.of(PATH_PARAMS_TEST, null, "controller-path-params-test.json"),
            Arguments.of(RECURSIVE, RECURSIVE_REV, "controller-recursive.json"),
            Arguments.of(STRING_TYPES, null, "controller-string-types.json"),
            Arguments.of(STRINGS_FROM_REGEX, null, "controller-string-from-regex.json"),
            Arguments.of(STRING_EXAMPLES_LENGTH, null, "controller-string-examples-length.json"),
            Arguments.of(TOASTER, TOASTER_REV, "controller-toaster.json"),
            Arguments.of(TOASTER_AUGMENTED, TOASTER_AUGMENTED_REV, "controller-toaster-augmented.json"),
            Arguments.of(TOASTER_SHORT, TOASTER_SHORT_REV, "controller-toaster2.json"),
            Arguments.of(TYPED_PARAMS, TYPED_PARAMS_REV, "controller-typed-params.json"),
            Arguments.of(TEST_CONTAINER_CHILDS,TEST_CONTAINER_CHILDS_REV, "controller-test-container-childs.json")
        );
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1' endpoint.
     */
    @Test
    void getMountDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/device-all.json");
        final var allModulesDoc = getMountDoc(0, 0, 0, 0);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1?width=1' endpoint.
     */
    @Test
    public void getMountDocWidthOneTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/device-all-width-one.json");
        final var allModulesDoc = getMountDoc(1, 0, 0, 0);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1?depth=1' endpoint.
     */
    @Test
    public void getMountDocDepthOneTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/device-all-depth-one.json");
        final var allModulesDoc = getMountDoc(0, 1, 0, 0);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1?width=1&depth=1' endpoint.
     */
    @Test
    public void getMountDocWidthAndDepthOneTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/device-all-width-and-depth-one.json");
        final var allModulesDoc = getMountDoc(1, 1, 0, 0);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1?offset=3&limit=5' endpoint. The offset is
     * set to 3 and the limit is set to 5, so we expect only 5 modules starting from the 4th module. The modules are
     * sorted by name. Expected modules are: duplication-test(index: 4), mandatory-test(index: 5), my-yang(index: 6),
     * path-params-test(index: 8) and recursive(index: 9). For modules duplication-service(index: 3) and
     * opflex(index: 7) we expect that they are not included in the result because they not contain any rpcs, containers
     * and lists.
     */
    @Test
    void getLimitedMountDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/device-offset3&limit5.json");
        final var allModulesDoc = getMountDoc(0, 0, 3, 5);
        JSONAssert.assertEquals(expectedJson, allModulesDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/moduleName' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    void getMountDocByModuleTest(final String moduleName, final String revision, final String jsonPath)
            throws Exception {
        final var expectedJson = getExpectedDoc("yang-document/" + jsonPath);
        final var moduleDoc = getMountDocByModule(moduleName, revision);
        JSONAssert.assertEquals(expectedJson, moduleDoc, IGNORE_ORDER);
    }

    private static Stream<Arguments> getMountDocByModuleTest() {
        return Stream.of(
            Arguments.of(ACTION_TYPES, null, "device-action-types.json"),
            Arguments.of(CHOICE_TEST, null, "device-choice-test.json"),
            Arguments.of(DEFINITION_TEST, null, "device-definition-test.json"),
            Arguments.of(DUPLICATION_SERVICE, null, "device-duplication-service.json"),
            Arguments.of(DUPLICATION_TEST, null, "device-duplication-test.json"),
            Arguments.of(MANDATORY_TEST, null, "device-mandatory-test.json"),
            Arguments.of(MY_YANG, MY_YANG_REV, "device-my-yang.json"),
            Arguments.of(OPFLEX, OPFLEX_REV, "device-opflex.json"),
            Arguments.of(PATH_PARAMS_TEST, null, "device-path-params-test.json"),
            Arguments.of(RECURSIVE, RECURSIVE_REV, "device-recursive.json"),
            Arguments.of(STRING_TYPES, null, "device-string-types.json"),
            Arguments.of(STRINGS_FROM_REGEX, null, "device-string-from-regex.json"),
            Arguments.of(STRING_EXAMPLES_LENGTH, null, "device-string-examples-length.json"),
            Arguments.of(TOASTER, TOASTER_REV, "device-toaster.json"),
            Arguments.of(TOASTER_AUGMENTED, TOASTER_AUGMENTED_REV, "device-toaster-augmented.json"),
            Arguments.of(TOASTER_SHORT, TOASTER_SHORT_REV, "device-toaster2.json"),
            Arguments.of(TYPED_PARAMS, TYPED_PARAMS_REV, "device-typed-params.json"),
            Arguments.of(TEST_CONTAINER_CHILDS,TEST_CONTAINER_CHILDS_REV, "device-test-container-childs.json")
        );
    }
}
