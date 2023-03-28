/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.opendaylight.restconf.openapi.AbstractOpenApiTest;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.common.Revision;

public final class OpenApiGeneratorRFC8040Test extends AbstractOpenApiTest {
    private static final String NAME = "toaster2";
    private static final String MY_YANG = "my-yang";
    private static final String MY_YANG_REVISION = "2022-10-06";
    private static final String REVISION_DATE = "2009-11-20";
    private static final String NAME_2 = "toaster";
    private static final String REVISION_DATE_2 = "2009-11-20";
    private static final String CHOICE_TEST_MODULE = "choice-test";
    private static final String PATH_PARAMS_TEST_MODULE = "path-params-test";
    private static final String RECURSIVE_TEST_MODULE = "recursive";

    private final OpenApiGeneratorRFC8040 generator = new OpenApiGeneratorRFC8040(SCHEMA_SERVICE);

    /**
     * Test that paths are generated according to the model.
     */
    @Test
    public void testPaths() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        assertEquals(Set.of("/rests/data",
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
            doc.getPaths().keySet());
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
        final OpenApiObject doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        for (final String path : configPaths) {
            final Path node = doc.getPaths().get(path);
            assertNotNull(node.getGet());
            assertNotNull(node.getPut());
            assertNotNull(node.getDelete());
            assertNotNull(node.getPost());
            assertNotNull(node.getPatch());
        }
    }

    /**
     * Test that generated document contains the following schemas.
     */
    @Test
    public void testSchemas() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        final Map<String, Schema> schemas = doc.getComponents().getSchemas();
        assertNotNull(schemas);

        final Schema configLst = schemas.get("toaster2_config_lst");
        assertNotNull(configLst);
        DocGenTestHelper.containsReferences(configLst, "lst1", "#/components/schemas/toaster2_lst_config_lst1");
        DocGenTestHelper.containsReferences(configLst, "cont1", "#/components/schemas/toaster2_lst_config_cont1");

        final Schema configLst1 = schemas.get("toaster2_lst_config_lst1");
        assertNotNull(configLst1);

        final Schema configCont1 = schemas.get("toaster2_lst_config_cont1");
        assertNotNull(configCont1);
        DocGenTestHelper.containsReferences(configCont1, "cont11",
                "#/components/schemas/toaster2_lst_cont1_config_cont11");
        DocGenTestHelper.containsReferences(configCont1, "lst11",
                "#/components/schemas/toaster2_lst_cont1_config_lst11");

        final Schema configCont11 = schemas.get("toaster2_lst_cont1_config_cont11");
        assertNotNull(configCont11);

        final Schema configLst11 = schemas.get("toaster2_lst_cont1_config_lst11");
        assertNotNull(configLst11);
    }

    /**
     * Test that generated document contains RPC schemas for "make-toast" with correct input.
     */
    @Test
    public void testRPC() {
        final var module = CONTEXT.findModule(NAME_2, Revision.of(REVISION_DATE_2)).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);
        assertNotNull(doc);

        final Map<String, Schema> schemas = doc.getComponents().getSchemas();
        final Schema input = schemas.get("toaster_make-toast_input");
        assertNotNull(input);
        final JsonNode properties = input.getProperties();
        assertTrue(properties.has("toasterDoneness"));
        assertTrue(properties.has("toasterToastType"));
    }

    @Test
    public void testChoice() {
        final var module = CONTEXT.findModule(CHOICE_TEST_MODULE).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);
        assertNotNull(doc);

        final var schemas = doc.getComponents().getSchemas();
        final Schema firstContainer = schemas.get("choice-test_config_first-container");
        assertEquals("default-value",
                firstContainer.getProperties().get("leaf-default").get("default").asText());
        assertFalse(firstContainer.getProperties().has("leaf-non-default"));

        final Schema secondContainer = schemas.get("choice-test_config_second-container");
        assertTrue(secondContainer.getProperties().has("leaf-first-case"));
        assertFalse(secondContainer.getProperties().has("leaf-second-case"));
    }

    /**
     * Test that checks for correct amount of parameters in requests.
     */
    @Test
    public void testRecursiveParameters() {
        final var configPaths = Map.of("/rests/data/recursive:container-root", 0,
            "/rests/data/recursive:container-root/root-list={name}", 1,
            "/rests/data/recursive:container-root/root-list={name}/nested-list={name1}", 2,
            "/rests/data/recursive:container-root/root-list={name}/nested-list={name1}/super-nested-list={name2}", 3);

        final var module = CONTEXT.findModule(RECURSIVE_TEST_MODULE, Revision.of("2023-05-22")).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);
        assertNotNull(doc);

        final var paths = doc.getPaths();
        assertEquals(5, paths.size());

        for (final var expectedPath : configPaths.entrySet()) {
            assertTrue(paths.containsKey(expectedPath.getKey()));
            final int expectedSize = expectedPath.getValue();

            final var path = paths.get(expectedPath.getKey());

            final var get = path.getGet();
            assertFalse(get.isMissingNode());
            assertEquals(expectedSize + 1, get.get("parameters").size());

            final var put = path.getPut();
            assertFalse(put.isMissingNode());
            assertEquals(expectedSize, put.get("parameters").size());

            final var delete = path.getDelete();
            assertFalse(delete.isMissingNode());
            assertEquals(expectedSize, delete.get("parameters").size());

            final var post = path.getPost();
            assertFalse(post.isMissingNode());
            assertEquals(expectedSize, post.get("parameters").size());

            final var patch = path.getPatch();
            assertFalse(patch.isMissingNode());
            assertEquals(expectedSize, patch.get("parameters").size());
        }
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
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        var pathToList1 = "/rests/data/path-params-test:cont/list1={name}";
        assertTrue(doc.getPaths().containsKey(pathToList1));
        assertEquals(List.of("name"), getPathParameters(doc.getPaths(), pathToList1));

        var pathToList2 = "/rests/data/path-params-test:cont/list1={name}/list2={name1}";
        assertTrue(doc.getPaths().containsKey(pathToList2));
        assertEquals(List.of("name", "name1"), getPathParameters(doc.getPaths(), pathToList2));

        var pathToList3 = "/rests/data/path-params-test:cont/list3={name}";
        assertTrue(doc.getPaths().containsKey(pathToList3));
        assertEquals(List.of("name"), getPathParameters(doc.getPaths(), pathToList3));

        var pathToList4 = "/rests/data/path-params-test:cont/list1={name}/list4={name1}";
        assertTrue(doc.getPaths().containsKey(pathToList4));
        assertEquals(List.of("name", "name1"), getPathParameters(doc.getPaths(), pathToList4));

        var pathToList5 = "/rests/data/path-params-test:cont/list1={name}/cont2";
        assertTrue(doc.getPaths().containsKey(pathToList4));
        assertEquals(List.of("name"), getPathParameters(doc.getPaths(), pathToList5));
    }

    @Test
    public void testSimpleSwaggerObjects() {
        final var module = CONTEXT.findModule(MY_YANG, Revision.of(MY_YANG_REVISION)).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        assertEquals(Set.of("/rests/data", "/rests/data/my-yang:data"), doc.getPaths().keySet());
        final var jsonNodeMyYangData = doc.getPaths().get("/rests/data/my-yang:data");
        final var myYangData = "#/components/schemas/my-yang_config_data";
        verifyRequestRef(jsonNodeMyYangData.getPost(), myYangData, myYangData);
        verifyRequestRef(jsonNodeMyYangData.getPut(), myYangData, myYangData);
        verifyRequestRef(jsonNodeMyYangData.getGet(), myYangData, myYangData);

        // Test `components/schemas` objects
        final var definitions = doc.getComponents().getSchemas();
        assertEquals(2, definitions.size());
        assertTrue(definitions.containsKey("my-yang_config_data"));
        assertTrue(definitions.containsKey("my-yang_module"));
    }

    @Test
    public void testToaster2SwaggerObjects() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        final var jsonNodeToaster = doc.getPaths().get("/rests/data/toaster2:toaster");
        final var toaster2 = "#/components/schemas/toaster2_config_toaster";
        verifyRequestRef(jsonNodeToaster.getPost(), toaster2, toaster2);
        verifyRequestRef(jsonNodeToaster.getPut(), toaster2, toaster2);
        verifyRequestRef(jsonNodeToaster.getGet(), toaster2, toaster2);

        final var jsonNodeToasterSlot = doc.getPaths().get("/rests/data/toaster2:toaster/toasterSlot={slotId}");
        final var toasterSlot2 = "#/components/schemas/toaster2_toaster_config_toasterSlot";
        verifyRequestRef(jsonNodeToasterSlot.getPost(), toasterSlot2, toasterSlot2);
        verifyRequestRef(jsonNodeToasterSlot.getPut(), toasterSlot2, toasterSlot2);
        verifyRequestRef(jsonNodeToasterSlot.getGet(), toasterSlot2, toasterSlot2);

        final var jsonNodeSlotInfo = doc.getPaths().get(
                "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo");
        final var slotInfo = "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo";
        verifyRequestRef(jsonNodeSlotInfo.getPost(), slotInfo, slotInfo);
        verifyRequestRef(jsonNodeSlotInfo.getPut(), slotInfo, slotInfo);
        verifyRequestRef(jsonNodeSlotInfo.getGet(), slotInfo, slotInfo);

        final var jsonNodeLst = doc.getPaths().get("/rests/data/toaster2:lst");
        final var lst = "#/components/schemas/toaster2_config_lst";
        verifyRequestRef(jsonNodeLst.getPost(), lst, lst);
        verifyRequestRef(jsonNodeLst.getPut(), lst, lst);
        verifyRequestRef(jsonNodeLst.getGet(), lst, lst);

        final var jsonNodeLst1 = doc.getPaths().get("/rests/data/toaster2:lst/lst1={key1},{key2}");
        final var lst1 = "#/components/schemas/toaster2_lst_config_lst1";
        verifyRequestRef(jsonNodeLst1.getPost(), lst1, lst1);
        verifyRequestRef(jsonNodeLst1.getPut(), lst1, lst1);
        verifyRequestRef(jsonNodeLst1.getGet(), lst1, lst1);

        final var jsonNodeMakeToast = doc.getPaths().get("/rests/operations/toaster2:make-toast");
        assertNull(jsonNodeMakeToast.getGet());
        final var makeToast = "#/components/schemas/toaster2_make-toast_input";
        verifyRequestRef(jsonNodeMakeToast.getPost(), makeToast, makeToast);

        final var jsonNodeCancelToast = doc.getPaths().get("/rests/operations/toaster2:cancel-toast");
        assertNull(jsonNodeCancelToast.getGet());
        // Test RPC with empty input
        final var postContent = jsonNodeCancelToast.getPost().get("requestBody").get("content");
        final var jsonSchema = postContent.get("application/json").get("schema");
        assertNull(jsonSchema.get("$ref"));
        assertEquals(2, jsonSchema.size());
        final var xmlSchema = postContent.get("application/xml").get("schema");
        assertNull(xmlSchema.get("$ref"));
        assertEquals(2, xmlSchema.size());

        // Test `components/schemas` objects
        final var definitions = doc.getComponents().getSchemas();
        assertEquals(18, definitions.size());
    }

    /**
     *  Test JSON and XML references for request operation.
     */
    public void verifyRequestRef(final JsonNode path, final String expectedJsonRef, final String expectedXmlRef) {
        final JsonNode postContent;
        if (path.get("requestBody") != null) {
            postContent = path.get("requestBody").get("content");
        } else {
            postContent = path.get("responses").get("200").get("content");
        }
        assertNotNull(postContent);
        final var postJsonRef = postContent.get("application/json").get("schema").get("$ref");
        assertNotNull(postJsonRef);
        assertEquals(expectedJsonRef, postJsonRef.textValue());
        final var postXmlRef = postContent.get("application/xml").get("schema").get("$ref");
        assertNotNull(postXmlRef);
        assertEquals(expectedXmlRef, postXmlRef.textValue());
    }
}
