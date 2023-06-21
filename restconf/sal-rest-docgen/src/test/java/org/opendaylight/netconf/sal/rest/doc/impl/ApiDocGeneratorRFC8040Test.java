/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.yang.common.Revision;

public final class ApiDocGeneratorRFC8040Test extends AbstractApiDocTest {
    private static final String NAME = "toaster2";
    private static final String REVISION_DATE = "2009-11-20";
    private static final String NAME_2 = "toaster";
    private static final String REVISION_DATE_2 = "2009-11-20";
    private static final String CHOICE_TEST_MODULE = "choice-test";
    private static final String PROPERTIES = "properties";
    private static final String PATH_PARAMS_TEST_MODULE = "path-params-test";
    private static final String MANDATORY_TEST = "mandatory-test";
    private static final String CONFIG_ROOT_CONTAINER = "mandatory-test_config_root-container";
    private static final String ROOT_CONTAINER = "mandatory-test_root-container";
    private static final String CONFIG_MANDATORY_CONTAINER = "mandatory-test_root-container_config_mandatory-container";
    private static final String MANDATORY_CONTAINER = "mandatory-test_root-container_mandatory-container";
    private static final String CONFIG_MANDATORY_LIST = "mandatory-test_root-container_config_mandatory-list";
    private static final String MANDATORY_LIST = "mandatory-test_root-container_mandatory-list";
    private static final String MANDATORY_TEST_MODULE = "mandatory-test_module";

    private final ApiDocGeneratorRFC8040 generator = new ApiDocGeneratorRFC8040(SCHEMA_SERVICE);

    /**
     * Test that paths are generated according to the model.
     */
    @Test
    public void testPaths() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final SwaggerObject doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
            ApiDocServiceImpl.OAversion.V2_0);

        assertEquals(List.of("/rests/data",
            "/rests/data/toaster2:toaster",
            "/rests/data/toaster2:toaster/toasterSlot={slotId}",
            "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo",
            "/rests/data/toaster2:lst",
            "/rests/data/toaster2:lst/cont1",
            "/rests/data/toaster2:lst/cont1/cont11",
            "/rests/data/toaster2:lst/cont1/lst11",
            "/rests/data/toaster2:lst/lst1={key1},{key2}",
            "/rests/operations/toaster2:make-toast",
            "/rests/operations/toaster2:cancel-toast",
            "/rests/operations/toaster2:restock-toaster"),
            ImmutableList.copyOf(doc.getPaths().fieldNames()));
    }

    /**
     * Test that generated configuration paths allow to use operations: get, put, patch, delete and post.
     */
    @Test
    public void testConfigPaths() {
        final List<String> configPaths = List.of("/rests/data/toaster2:lst",
                "/rests/data/toaster2:lst/cont1",
                "/rests/data/toaster2:lst/cont1/cont11",
                "/rests/data/toaster2:lst/cont1/lst11",
                "/rests/data/toaster2:lst/lst1={key1},{key2}");

        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final SwaggerObject doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
            ApiDocServiceImpl.OAversion.V2_0);

        for (final String path : configPaths) {
            final JsonNode node = doc.getPaths().get(path);
            assertFalse(node.path("get").isMissingNode());
            assertFalse(node.path("put").isMissingNode());
            assertFalse(node.path("delete").isMissingNode());
            assertFalse(node.path("post").isMissingNode());
            assertFalse(node.path("patch").isMissingNode());
        }
    }

    /**
     * Test that generated document contains the following definitions.
     */
    @Test
    public void testDefinitions() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final SwaggerObject doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
            ApiDocServiceImpl.OAversion.V2_0);

        final ObjectNode definitions = doc.getDefinitions();
        assertNotNull(definitions);

        final JsonNode configLstTop = definitions.get("toaster2_config_lst_TOP");
        assertNotNull(configLstTop);
        DocGenTestHelper.containsReferences(configLstTop, "lst", "#/definitions/toaster2_config_lst");

        final JsonNode configLst = definitions.get("toaster2_config_lst");
        assertNotNull(configLst);
        DocGenTestHelper.containsReferences(configLst, "lst1", "#/definitions/toaster2_lst_config_lst1");
        DocGenTestHelper.containsReferences(configLst, "cont1", "#/definitions/toaster2_lst_config_cont1");

        final JsonNode configLst1Top = definitions.get("toaster2_lst_config_lst1_TOP");
        assertNotNull(configLst1Top);
        DocGenTestHelper.containsReferences(configLst1Top, "lst1", "#/definitions/toaster2_lst_config_lst1");

        final JsonNode configLst1 = definitions.get("toaster2_lst_config_lst1");
        assertNotNull(configLst1);

        final JsonNode configCont1Top = definitions.get("toaster2_lst_config_cont1_TOP");
        assertNotNull(configCont1Top);
        DocGenTestHelper.containsReferences(configCont1Top, "cont1", "#/definitions/toaster2_lst_config_cont1");

        final JsonNode configCont1 = definitions.get("toaster2_lst_config_cont1");
        assertNotNull(configCont1);
        DocGenTestHelper.containsReferences(configCont1, "cont11", "#/definitions/toaster2_lst_cont1_config_cont11");
        DocGenTestHelper.containsReferences(configCont1, "lst11", "#/definitions/toaster2_lst_cont1_config_lst11");

        final JsonNode configCont11Top = definitions.get("toaster2_lst_cont1_config_cont11_TOP");
        assertNotNull(configCont11Top);
        DocGenTestHelper.containsReferences(configCont11Top,
            "cont11", "#/definitions/toaster2_lst_cont1_config_cont11");

        final JsonNode configCont11 = definitions.get("toaster2_lst_cont1_config_cont11");
        assertNotNull(configCont11);

        final JsonNode configLst11Top = definitions.get("toaster2_lst_cont1_config_lst11_TOP");
        assertNotNull(configLst11Top);
        DocGenTestHelper.containsReferences(configLst11Top, "lst11", "#/definitions/toaster2_lst_cont1_config_lst11");

        final JsonNode configLst11 = definitions.get("toaster2_lst_cont1_config_lst11");
        assertNotNull(configLst11);
    }

    /**
     * Test that generated document contains RPC definition for "make-toast" with correct input.
     */
    @Test
    public void testRPC() {
        final var module = CONTEXT.findModule(NAME_2, Revision.of(REVISION_DATE_2)).orElseThrow();
        final SwaggerObject doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
            ApiDocServiceImpl.OAversion.V2_0);
        assertNotNull(doc);

        final ObjectNode definitions = doc.getDefinitions();
        final JsonNode inputTop = definitions.get("toaster_make-toast_input_TOP");
        assertNotNull(inputTop);
        final String testString = "{\"input\":{\"$ref\":\"#/definitions/toaster_make-toast_input\"}}";
        assertEquals(testString, inputTop.get("properties").toString());
        final JsonNode input = definitions.get("toaster_make-toast_input");
        final JsonNode properties = input.get("properties");
        assertTrue(properties.has("toasterDoneness"));
        assertTrue(properties.has("toasterToastType"));
    }

    @Test
    public void testChoice() {
        final var module = CONTEXT.findModule(CHOICE_TEST_MODULE).orElseThrow();
        final var doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
                ApiDocServiceImpl.OAversion.V2_0);
        assertNotNull(doc);

        final var definitions = doc.getDefinitions();
        JsonNode firstContainer = definitions.get("choice-test_first-container");
        assertEquals("default-value",
                firstContainer.get(PROPERTIES).get("leaf-default").get("default").asText());
        assertFalse(firstContainer.get(PROPERTIES).has("leaf-non-default"));

        JsonNode secondContainer = definitions.get("choice-test_second-container");
        assertTrue(secondContainer.get(PROPERTIES).has("leaf-first-case"));
        assertFalse(secondContainer.get(PROPERTIES).has("leaf-second-case"));
    }

    @Test
    public void testMandatory() {
        final var module = CONTEXT.findModule(MANDATORY_TEST).orElseThrow();
        final var doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
                ApiDocServiceImpl.OAversion.V3_0);
        assertNotNull(doc);
        final var definitions = doc.getDefinitions();
        final var containersWithRequired = new ArrayList<String>();

        final var reqRootContainerElements = Set.of("mandatory-root-leaf", "mandatory-container",
            "mandatory-first-choice", "mandatory-list");
        verifyRequiredField(definitions.get(CONFIG_ROOT_CONTAINER), reqRootContainerElements);
        containersWithRequired.add(CONFIG_ROOT_CONTAINER);
        verifyRequiredField(definitions.get(ROOT_CONTAINER), reqRootContainerElements);
        containersWithRequired.add(ROOT_CONTAINER);

        final var reqMandatoryContainerElements = Set.of("mandatory-leaf", "leaf-list-with-min-elements");
        verifyRequiredField(definitions.get(CONFIG_MANDATORY_CONTAINER), reqMandatoryContainerElements);
        containersWithRequired.add(CONFIG_MANDATORY_CONTAINER);
        verifyRequiredField(definitions.get(MANDATORY_CONTAINER), reqMandatoryContainerElements);
        containersWithRequired.add(MANDATORY_CONTAINER);

        final var reqMandatoryListElements = Set.of("mandatory-list-field");
        verifyRequiredField(definitions.get(CONFIG_MANDATORY_LIST), reqMandatoryListElements);
        containersWithRequired.add(CONFIG_MANDATORY_LIST);
        verifyRequiredField(definitions.get(MANDATORY_LIST), reqMandatoryListElements);
        containersWithRequired.add(MANDATORY_LIST);

        final var testModuleMandatoryArray = Set.of("root-container", "root-mandatory-list");
        verifyRequiredField(definitions.get(MANDATORY_TEST_MODULE), testModuleMandatoryArray);
        containersWithRequired.add(MANDATORY_TEST_MODULE);

        verifyRecursivelyThatPropertyDoesNotHaveRequired(containersWithRequired, definitions);
    }

    /**
     * Test that request parameters are correctly numbered.
     *
     * <p>
     * It means we should have name and name1, etc. when we have the same parameter in path multiple times.
     */
    @Test
    public void testParametersNumbering() {
        final var module = CONTEXT.findModule(PATH_PARAMS_TEST_MODULE).orElseThrow();
        final var doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
                ApiDocServiceImpl.OAversion.V3_0);

        var pathToList1 = "/rests/data/path-params-test:cont/list1={name}";
        assertTrue(doc.getPaths().has(pathToList1));
        assertEquals(List.of("name"), getPathParameters(doc.getPaths(), pathToList1));

        var pathToList2 = "/rests/data/path-params-test:cont/list1={name}/list2={name1}";
        assertTrue(doc.getPaths().has(pathToList2));
        assertEquals(List.of("name", "name1"), getPathParameters(doc.getPaths(), pathToList2));

        var pathToList3 = "/rests/data/path-params-test:cont/list3={name}";
        assertTrue(doc.getPaths().has(pathToList3));
        assertEquals(List.of("name"), getPathParameters(doc.getPaths(), pathToList3));

        var pathToList4 = "/rests/data/path-params-test:cont/list1={name}/list4={name1}";
        assertTrue(doc.getPaths().has(pathToList4));
        assertEquals(List.of("name", "name1"), getPathParameters(doc.getPaths(), pathToList4));

        var pathToList5 = "/rests/data/path-params-test:cont/list1={name}/cont2";
        assertTrue(doc.getPaths().has(pathToList4));
        assertEquals(List.of("name"), getPathParameters(doc.getPaths(), pathToList5));
    }

    private static void verifyRecursivelyThatPropertyDoesNotHaveRequired(final List<String> expected,
        final ObjectNode definitions) {
        final var fields = definitions.fields();
        while (fields.hasNext()) {
            final var next = fields.next();
            final var nodeName = next.getKey();
            final var jsonNode = next.getValue();
            if (expected.contains(nodeName)) {
                continue;
            }
            assertNull("Json node " + nodeName + " should not have 'required' field in body",
                jsonNode.get("required"));
        }
    }

    private static void verifyRequiredField(final JsonNode rootContainer, final Set<String> expected) {
        assertNotNull(rootContainer);
        final var required = rootContainer.get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        final var actualContainerArray = StreamSupport.stream(required.spliterator(), false)
            .map(JsonNode::textValue)
            .collect(Collectors.toSet());
        assertEquals(expected, actualContainerArray);
    }
}
