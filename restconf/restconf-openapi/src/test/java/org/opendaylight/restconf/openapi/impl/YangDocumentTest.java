/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.glassfish.jersey.internal.util.collection.ImmutableMultivaluedMap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class YangDocumentTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * We want flexibility in comparing the resulting JSONs by not enforcing strict ordering of array contents.
     * This comparison mode allows us to do that and also to restrict extensibility (extensibility = additional fields)
     */
    private static final JSONCompareMode IGNORE_ORDER = JSONCompareMode.NON_EXTENSIBLE;
    private static final String ACTION_TYPES = "action-types";
    private static final String CHOICE_TEST = "choice-test";
    private static final String DEFINITION_TEST = "definition-test";
    private static final String MANDATORY_TEST = "mandatory-test";
    private static final String MY_YANG = "my-yang";
    private static final String MY_YANG_REV = "2022-10-06";
    private static final String OPFLEX = "opflex";
    private static final String OPFLEX_REV = "2014-05-28";
    private static final String PATH_PARAMS_TEST = "path-params-test";
    private static final String RECURSIVE = "recursive";
    private static final String RECURSIVE_REV = "2023-05-22";
    private static final String STRING_TYPES = "string-types";
    private static final String STRINGS_FROM_REGEX = "strings-from-regex";
    private static final String TEST_CONTAINER_CHILDS = "test-container-childs";
    private static final String TEST_CONTAINER_CHILDS_REV = "2023-09-28";
    private static final String TOASTER = "toaster";
    private static final String TOASTER_REV = "2009-11-20";
    private static final String TOASTER_AUGMENTED = "toaster-augmented";
    private static final String TOASTER_AUGMENTED_REV = "2014-07-14";
    private static final String TOASTER_SHORT = "toaster2";
    private static final String TOASTER_SHORT_REV = "2009-11-20";
    private static final String TYPED_PARAMS = "typed-params";
    private static final String TYPED_PARAMS_REV = "2023-10-24";

    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private static OpenApiService openApiService;

    @BeforeClass
    public static void beforeClass() {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResourceDirectory("/yang/");
        when(schemaService.getGlobalContext()).thenReturn(context);

        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final var service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService, service);
        final var openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService);
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(INSTANCE_ID);
        openApiService = new OpenApiServiceImpl(mountPointRFC8040, openApiGeneratorRFC8040);
    }

    /**
     * Tests the swagger document that is result of the call to the '/single' endpoint.
     */
    @Test
    public void getAllModulesDocTest() throws Exception {
        final var getAllController = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/single");
        final var controllerDocAll = openApiService.getAllModulesDoc(getAllController).getEntity();

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocAll);
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-all.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/action-types' endpoint.
     *
     * <p>
     * Model action-types is used for test correct generating of action statements for openapi.
     */
    @Test
    public void getDocActionTypesTest() throws Exception {
        final var getActionTypesController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/action-types");
        final var controllerDocActionTypes = openApiService.getDocByModule(ACTION_TYPES, null,
            getActionTypesController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocActionTypes.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-action-types.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/choice-test' endpoint.
     *
     * <p>
     * Model choice-test is used for test correct generation of action statements for openapi.
     */
    @Test
    public void getDocChoiceTest() throws Exception {
        final var getChoiceTestController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/choice-test");
        final var controllerDocOperational = openApiService.getDocByModule(CHOICE_TEST, null,
            getChoiceTestController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-choice-test.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/definition-test' endpoint.
     *
     * <p>
     * Model definition-test is used for test correct generating of definition for nodes and operations for openapi.
     */
    @Test
    public void getDocDefinitionTest() throws Exception {
        final var getDefinitionTestController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/definition-test");
        final var controllerDocOperational = openApiService.getDocByModule(DEFINITION_TEST, null,
            getDefinitionTestController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-definition-test.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mandatory-test' endpoint.
     *
     * <p>
     * Model mandatory-test is used for test correct generating of mandatory nodes for openapi.
     */
    @Test
    public void getDocMandatoryTest() throws Exception {
        final var getMandatoryTestController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mandatory-test");
        final var controllerDocOperational = openApiService.getDocByModule(MANDATORY_TEST, null,
            getMandatoryTestController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-mandatory-test.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }


    /**
     * Tests the swagger document that is result of the call to the '/my-yang(2022-10-06)' endpoint.
     *
     * <p>
     * Model my-yang is used for test correct generating of simple openapi object.
     */
    @Test
    public void getDocMyYangTest() throws Exception {
        final var getMyYangController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/my-yang(2022-10-06)");
        final var controllerDocOperational = openApiService.getDocByModule(MY_YANG, MY_YANG_REV,
            getMyYangController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-my-yang.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/opflex' endpoint.
     *
     * <p>
     * Model opflex defines the group-based policy OpFlex renderer model.
     */
    @Test
    public void getDocOpflexTest() throws Exception {
        final var getOpflexController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/opflex");
        final var controllerDocOperational = openApiService.getDocByModule(OPFLEX, OPFLEX_REV,
            getOpflexController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-opflex.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/path-params-test' endpoint.
     *
     * <p>
     * Model path-params-test is used for test correct generating of parameters numbering for openapi.
     */
    @Test
    public void getDocPathParamsTest() throws Exception {
        final var getPathParamsController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/path-params-test");
        final var controllerDocOperational = openApiService.getDocByModule(PATH_PARAMS_TEST, null,
            getPathParamsController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-path-params-test.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/recursive' endpoint.
     *
     * <p>
     * Model recursive is used for test correct generating of recursive parameters for openapi.
     */
    @Test
    public void getDocRecursiveTest() throws Exception {
        final var getRecursiveController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/recursive");
        final var controllerDocOperational = openApiService.getDocByModule(RECURSIVE, RECURSIVE_REV,
            getRecursiveController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-recursive.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/string-types' endpoint.
     *
     * <p>
     * Model string-types is used for test correct generating of types with restrictions for openapi.
     */
    @Test
    public void getDocStringTypesTest() throws Exception {
        final var getStringTypesController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/string-types");
        final var controllerDocOperational = openApiService.getDocByModule(STRING_TYPES, null,
            getStringTypesController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-string-types.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/strings-from-regex' endpoint.
     *
     * <p>
     * Model strings-from-regex is used for test correct generating of string patterns for openapi.
     */
    @Test
    public void getDocStringFromRegexTest() throws Exception {
        final var getStringFromRegexController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/strings-from-regex");
        final var controllerDocOperational = openApiService.getDocByModule(STRINGS_FROM_REGEX, null,
            getStringFromRegexController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-string-from-regex.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster' endpoint.
     *
     * <p>
     * Model toaster is used for test correct generating of complex openapi object.
     */
    @Test
    public void getDocToasterTest() throws Exception {
        final var getToasterController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/toaster");
        final var controllerDocOperational = openApiService.getDocByModule(TOASTER, TOASTER_REV,
            getToasterController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-toaster.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster-augmented' endpoint.
     *
     * <p>
     * Model toaster_augmented is used for test correct generating of augmented model for openapi.
     */
    @Test
    public void getDocToasterAugmentedTest() throws Exception {
        final var getToasterAugmentedController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/toaster-augmented");
        final var controllerDocOperational = openApiService.getDocByModule(TOASTER_AUGMENTED, TOASTER_AUGMENTED_REV,
            getToasterAugmentedController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-toaster-augmented.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster-short' endpoint.
     *
     * <p>
     * Model toaster_short is used for test correct generating of types with restrictions for openapi.
     */
    @Test
    public void getDocToasterShortTest() throws Exception {
        final var getToasterShortController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/toaster2");
        final var controllerDocOperational = openApiService.getDocByModule(TOASTER_SHORT, TOASTER_SHORT_REV,
            getToasterShortController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-toaster2.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/typed-params' endpoint.
     *
     * <p>
     * Model typed-params is used for test correct generating of all built-in types and assignment to allowed
     * types for openapi.
     */
    @Test
    public void getDocTypedParamsTest() throws Exception {
        final var getTypedParamsController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/typed-params");
        final var controllerDocOperational = openApiService.getDocByModule(TYPED_PARAMS, TYPED_PARAMS_REV,
            getTypedParamsController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-typed-params.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/test-container-childs' endpoint.
     *
     * <p>
     * Model test-container-childs is used for test correct generating of min-elements, max-elements and example
     * elements for openapi.
     */
    @Test
    public void getDocContainerChildsTest() throws Exception {
        final var getTypedParamsController = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/test-container-childs");
        final var controllerDocOperational = openApiService.getDocByModule(TEST_CONTAINER_CHILDS,
            TEST_CONTAINER_CHILDS_REV, getTypedParamsController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/controller-test-container-childs.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1' endpoint.
     */
    @Test
    public void getMountDocTest() throws Exception {
        final var getAllDevice = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/mounts/1");
        when(getAllDevice.getQueryParameters()).thenReturn(ImmutableMultivaluedMap.empty());
        final var deviceDocAll = openApiService.getMountDoc("1", getAllDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocAll.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-all.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/action-types' endpoint.
     *
     * <p>
     * Model action-types is used for test correct generating of action statements for openapi.
     */
    @Test
    public void getMountDocActionTypesTest() throws Exception {
        final var getActionTypesDevice = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/mounts/1/action-types");
        final var deviceDocActionTypes = openApiService.getMountDocByModule("1", ACTION_TYPES, null,
            getActionTypesDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocActionTypes.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-action-types.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/choice-test' endpoint.
     *
     * <p>
     * Model choice-test is used for test correct generation of action statements for openapi.
     */
    @Test
    public void getMountDocChoiceTest() throws Exception {
        final var getChoiceTestDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/choice-test");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", CHOICE_TEST, null,
            getChoiceTestDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-choice-test.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/definition-test' endpoint.
     *
     * <p>
     * Model definition-test is used for test correct generating of definition for nodes and operations for openapi.
     */
    @Test
    public void getMountDocDefinitionTest() throws Exception {
        final var getDefinitionTestDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/definition-test");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", DEFINITION_TEST, null,
            getDefinitionTestDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-definition-test.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/mandatory-test' endpoint.
     *
     * <p>
     * Model mandatory-test is used for test correct generating of mandatory nodes for openapi.
     */
    @Test
    public void getMountDocMandatoryTest() throws Exception {
        final var getMandatoryTestDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/mandatory-test");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", MANDATORY_TEST, null,
            getMandatoryTestDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-mandatory-test.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }


    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/my-yang(2022-10-06)' endpoint.
     *
     * <p>
     * Model my-yang is used for test correct generating of simple openapi object.
     */
    @Test
    public void getMountDocMyYangTest() throws Exception {
        final var getMyYangDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/my-yang(2022-10-06)");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", MY_YANG, MY_YANG_REV,
            getMyYangDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-my-yang.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/opflex' endpoint.
     *
     * <p>
     * Model opflex defines the group-based policy OpFlex renderer model.
     */
    @Test
    public void getMountDocOpflexTest() throws Exception {
        final var getOpflexDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/opflex");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", OPFLEX, OPFLEX_REV,
            getOpflexDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-opflex.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/path-params-test' endpoint.
     *
     * <p>
     * Model path-params-test is used for test correct generating of parameters numbering for openapi.
     */
    @Test
    public void getMountDocPathParamsTest() throws Exception {
        final var getPathParamsDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/path-params-test");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", PATH_PARAMS_TEST, null,
            getPathParamsDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-path-params-test.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/recursive' endpoint.
     *
     * <p>
     * Model recursive is used for test correct generating of recursive parameters for openapi.
     */
    @Test
    public void getMountDocRecursiveTest() throws Exception {
        final var getRecursiveDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/recursive");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", RECURSIVE, RECURSIVE_REV,
            getRecursiveDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-recursive.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/string-types' endpoint.
     *
     * <p>
     * Model string-types is used for test correct generating of types with restrictions for openapi.
     */
    @Test
    public void getMountDocStringTypesTest() throws Exception {
        final var getStringTypesDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/string-types");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", STRING_TYPES, null,
            getStringTypesDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-string-types.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/string-from-regex' endpoint.
     *
     * <p>
     * Model strings-from-regex is used for test correct generating of string patterns for openapi.
     */
    @Test
    public void getMountDocStringFromRegexTest() throws Exception {
        final var getStringFromRegexDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/string-from-regex");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", STRINGS_FROM_REGEX, null,
            getStringFromRegexDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-string-from-regex.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster' endpoint.
     *
     * <p>
     * Model toaster is used for test correct generating of complex openapi object.
     */
    @Test
    public void getMountDocToasterTest() throws Exception {
        final var getToasterDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/toaster");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", TOASTER, TOASTER_REV,
            getToasterDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-toaster.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster-augmented' endpoint.
     *
     * <p>
     * Model toaster_augmented is used for test correct generating of augmented model for openapi.
     */
    @Test
    public void getMountDocToasterAugmentedTest() throws Exception {
        final var getToasterAugmentedDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/toaster-augmented");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", TOASTER_AUGMENTED,
            TOASTER_AUGMENTED_REV, getToasterAugmentedDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-toaster-augmented.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster-short' endpoint.
     *
     * <p>
     * Model toaster_short is used for test correct generating of types with restrictions for openapi.
     */
    @Test
    public void getMountDocToasterShortTest() throws Exception {
        final var getToasterShortDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/toaster2");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", TOASTER_SHORT, TOASTER_SHORT_REV,
            getToasterShortDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-toaster2.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/typed-params' endpoint.
     *
     * <p>
     * Model typed-params is used for test correct generating of all built-in types and assignment to allowed
     * types for openapi.
     */
    @Test
    public void getMountDocTypedParamsTest() throws Exception {
        final var getTypedParamsDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/typed-params");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", TYPED_PARAMS, TYPED_PARAMS_REV,
            getTypedParamsDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-typed-params.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/test-container-childs' endpoint.
     *
     * <p>
     * Model test-container-childs is used for test correct generating of min-elements, max-elements and example
     * elements for openapi.
     */
    @Test
    public void getMountDocContainerChildsTest() throws Exception {
        final var getTypedParamsDevice = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/1/test-container-childs");
        final var deviceDocOperational = openApiService.getMountDocByModule("1", TEST_CONTAINER_CHILDS,
            TEST_CONTAINER_CHILDS_REV, getTypedParamsDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocOperational.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("yang-document/device-test-container-childs.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }
}
