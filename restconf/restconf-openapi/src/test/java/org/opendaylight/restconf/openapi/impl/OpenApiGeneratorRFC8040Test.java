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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.openapi.OpenApiTestUtils.getPathParameters;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public final class OpenApiGeneratorRFC8040Test {
    private static final String TOASTER_2 = "toaster2";
    private static final String REVISION_DATE = "2009-11-20";

    private static EffectiveModelContext context;
    private static DOMSchemaService schemaService;

    private final OpenApiGeneratorRFC8040 generator = new OpenApiGeneratorRFC8040(schemaService);

    @BeforeClass
    public static void beforeClass() {
        schemaService = mock(DOMSchemaService.class);
        context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        when(schemaService.getGlobalContext()).thenReturn(context);
    }

    /**
     * Test that paths are generated according to the model.
     */
    @Test
    public void testPaths() {
        final var module = context.findModule(TOASTER_2, Revision.of(REVISION_DATE)).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);

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
            doc.paths().keySet());
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

        final var module = context.findModule(TOASTER_2, Revision.of(REVISION_DATE)).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);

        for (final String path : configPaths) {
            final Path node = doc.paths().get(path);
            assertNotNull(node.get());
            assertNotNull(node.put());
            assertNotNull(node.delete());
            assertNotNull(node.post());
            assertNotNull(node.patch());
        }
    }

    /**
     * Test that generated document contains the following schemas.
     */
    @Test
    public void testSchemas() {
        final var module = context.findModule(TOASTER_2, Revision.of(REVISION_DATE)).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);

        final Map<String, Schema> schemas = doc.components().schemas();
        assertNotNull(schemas);

        final Schema configLstTop = schemas.get("toaster2_config_lst_TOP");
        assertNotNull(configLstTop);
        DocGenTestHelper.containsReferences(configLstTop, "lst", "#/components/schemas/toaster2_config_lst");

        final Schema configLst = schemas.get("toaster2_config_lst");
        assertNotNull(configLst);
        DocGenTestHelper.containsReferences(configLst, "lst1", "#/components/schemas/toaster2_lst_config_lst1");
        DocGenTestHelper.containsReferences(configLst, "cont1", "#/components/schemas/toaster2_lst_config_cont1");

        final Schema configLst1Top = schemas.get("toaster2_lst_config_lst1_TOP");
        assertNotNull(configLst1Top);
        DocGenTestHelper.containsReferences(configLst1Top, "lst1", "#/components/schemas/toaster2_lst_config_lst1");

        final Schema configLst1 = schemas.get("toaster2_lst_config_lst1");
        assertNotNull(configLst1);

        final Schema configCont1Top = schemas.get("toaster2_lst_config_cont1_TOP");
        assertNotNull(configCont1Top);
        DocGenTestHelper.containsReferences(configCont1Top, "cont1", "#/components/schemas/toaster2_lst_config_cont1");

        final Schema configCont1 = schemas.get("toaster2_lst_config_cont1");
        assertNotNull(configCont1);
        DocGenTestHelper.containsReferences(configCont1, "cont11",
                "#/components/schemas/toaster2_lst_cont1_config_cont11");
        DocGenTestHelper.containsReferences(configCont1, "lst11",
                "#/components/schemas/toaster2_lst_cont1_config_lst11");

        final Schema configCont11Top = schemas.get("toaster2_lst_cont1_config_cont11_TOP");
        assertNotNull(configCont11Top);
        DocGenTestHelper.containsReferences(configCont11Top,
                "cont11", "#/components/schemas/toaster2_lst_cont1_config_cont11");

        final Schema configCont11 = schemas.get("toaster2_lst_cont1_config_cont11");
        assertNotNull(configCont11);

        final Schema configLst11Top = schemas.get("toaster2_lst_cont1_config_lst11_TOP");
        assertNotNull(configLst11Top);
        DocGenTestHelper.containsReferences(configLst11Top, "lst11",
                "#/components/schemas/toaster2_lst_cont1_config_lst11");

        final Schema configLst11 = schemas.get("toaster2_lst_cont1_config_lst11");
        assertNotNull(configLst11);
    }

    /**
     * Test that generated document contains RPC schemas for "make-toast" with correct input.
     */
    @Test
    public void testRPC() {
        final var module = context.findModule("toaster", Revision.of("2009-11-20")).orElseThrow();
        final OpenApiObject doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);
        assertNotNull(doc);

        final Map<String, Schema> schemas = doc.components().schemas();
        final Schema inputTop = schemas.get("toaster_make-toast_input_TOP");
        assertNotNull(inputTop);
        final String testString = "{\"input\":{\"$ref\":\"#/components/schemas/toaster_make-toast_input\"}}";
        assertEquals(testString, inputTop.properties().toString());
        final Schema input = schemas.get("toaster_make-toast_input");
        final JsonNode properties = input.properties();
        assertTrue(properties.has("toasterDoneness"));
        assertTrue(properties.has("toasterToastType"));
    }

    @Test
    public void testChoice() {
        final var module = context.findModule("choice-test").orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);
        assertNotNull(doc);

        final var schemas = doc.components().schemas();
        final Schema firstContainer = schemas.get("choice-test_first-container");
        assertEquals("default-value",
                firstContainer.properties().get("leaf-default").get("default").asText());
        assertFalse(firstContainer.properties().has("leaf-non-default"));

        final Schema secondContainer = schemas.get("choice-test_second-container");
        assertTrue(secondContainer.properties().has("leaf-first-case"));
        assertFalse(secondContainer.properties().has("leaf-second-case"));
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

        final var module = context.findModule("recursive", Revision.of("2023-05-22")).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);
        assertNotNull(doc);

        final var paths = doc.paths();
        assertEquals(5, paths.size());

        for (final var expectedPath : configPaths.entrySet()) {
            assertTrue(paths.containsKey(expectedPath.getKey()));
            final int expectedSize = expectedPath.getValue();

            final var path = paths.get(expectedPath.getKey());

            final var get = path.get();
            assertFalse(get.isMissingNode());
            assertEquals(expectedSize + 1, get.get("parameters").size());

            final var put = path.put();
            assertFalse(put.isMissingNode());
            assertEquals(expectedSize, put.get("parameters").size());

            final var delete = path.delete();
            assertFalse(delete.isMissingNode());
            assertEquals(expectedSize, delete.get("parameters").size());

            final var post = path.post();
            assertFalse(post.isMissingNode());
            assertEquals(expectedSize, post.get("parameters").size());

            final var patch = path.patch();
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
        final var module = context.findModule("path-params-test").orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);

        var pathToList1 = "/rests/data/path-params-test:cont/list1={name}";
        assertTrue(doc.paths().containsKey(pathToList1));
        assertEquals(List.of("name"), getPathParameters(doc.paths(), pathToList1));

        var pathToList2 = "/rests/data/path-params-test:cont/list1={name}/list2={name1}";
        assertTrue(doc.paths().containsKey(pathToList2));
        assertEquals(List.of("name", "name1"), getPathParameters(doc.paths(), pathToList2));

        var pathToList3 = "/rests/data/path-params-test:cont/list3={name}";
        assertTrue(doc.paths().containsKey(pathToList3));
        assertEquals(List.of("name"), getPathParameters(doc.paths(), pathToList3));

        var pathToList4 = "/rests/data/path-params-test:cont/list1={name}/list4={name1}";
        assertTrue(doc.paths().containsKey(pathToList4));
        assertEquals(List.of("name", "name1"), getPathParameters(doc.paths(), pathToList4));

        var pathToList5 = "/rests/data/path-params-test:cont/list1={name}/cont2";
        assertTrue(doc.paths().containsKey(pathToList4));
        assertEquals(List.of("name"), getPathParameters(doc.paths(), pathToList5));
    }

    @Test
    public void testSimpleOpenApiObjects() {
        final var module = context.findModule("my-yang", Revision.of("2022-10-06")).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);

        assertEquals(Set.of("/rests/data", "/rests/data/my-yang:data"), doc.paths().keySet());
        final var JsonNodeMyYangData = doc.paths().get("/rests/data/my-yang:data");
        verifyRequestRef(JsonNodeMyYangData.post(), "#/components/schemas/my-yang_config_data",
                "#/components/schemas/my-yang_config_data");
        verifyRequestRef(JsonNodeMyYangData.put(), "#/components/schemas/my-yang_config_data_TOP",
                "#/components/schemas/my-yang_config_data");
        verifyRequestRef(JsonNodeMyYangData.get(), "#/components/schemas/my-yang_data_TOP",
                "#/components/schemas/my-yang_data_TOP");

        // Test `components/schemas` objects
        final var definitions = doc.components().schemas();
        assertEquals(5, definitions.size());
        assertTrue(definitions.containsKey("my-yang_config_data"));
        assertTrue(definitions.containsKey("my-yang_config_data_TOP"));
        assertTrue(definitions.containsKey("my-yang_data"));
        assertTrue(definitions.containsKey("my-yang_data_TOP"));
        assertTrue(definitions.containsKey("my-yang_module"));
    }

    @Test
    public void testToaster2OpenApiObjects() {
        final var module = context.findModule(TOASTER_2, Revision.of(REVISION_DATE)).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);

        final var jsonNodeToaster = doc.paths().get("/rests/data/toaster2:toaster");
        verifyRequestRef(jsonNodeToaster.post(), "#/components/schemas/toaster2_config_toaster",
                "#/components/schemas/toaster2_config_toaster");
        verifyRequestRef(jsonNodeToaster.put(), "#/components/schemas/toaster2_config_toaster_TOP",
                "#/components/schemas/toaster2_config_toaster");
        verifyRequestRef(jsonNodeToaster.get(), "#/components/schemas/toaster2_toaster_TOP",
                "#/components/schemas/toaster2_toaster_TOP");

        final var jsonNodeToasterSlot = doc.paths().get("/rests/data/toaster2:toaster/toasterSlot={slotId}");
        verifyRequestRef(jsonNodeToasterSlot.post(), "#/components/schemas/toaster2_toaster_config_toasterSlot",
                "#/components/schemas/toaster2_toaster_config_toasterSlot");
        verifyRequestRef(jsonNodeToasterSlot.put(), "#/components/schemas/toaster2_toaster_config_toasterSlot_TOP",
                "#/components/schemas/toaster2_toaster_config_toasterSlot");
        verifyRequestRef(jsonNodeToasterSlot.get(), "#/components/schemas/toaster2_toaster_toasterSlot_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_TOP");

        final var jsonNodeSlotInfo = doc.paths().get(
                "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.post(),
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo",
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.put(),
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.get(), "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo_TOP");

        final var jsonNodeLst = doc.paths().get("/rests/data/toaster2:lst");
        verifyRequestRef(jsonNodeLst.post(), "#/components/schemas/toaster2_config_lst",
                "#/components/schemas/toaster2_config_lst");
        verifyRequestRef(jsonNodeLst.put(), "#/components/schemas/toaster2_config_lst_TOP",
                "#/components/schemas/toaster2_config_lst");
        verifyRequestRef(jsonNodeLst.get(), "#/components/schemas/toaster2_lst_TOP",
                "#/components/schemas/toaster2_lst_TOP");

        final var jsonNodeLst1 = doc.paths().get("/rests/data/toaster2:lst/lst1={key1},{key2}");
        verifyRequestRef(jsonNodeLst1.post(), "#/components/schemas/toaster2_lst_config_lst1",
                "#/components/schemas/toaster2_lst_config_lst1");
        verifyRequestRef(jsonNodeLst1.put(), "#/components/schemas/toaster2_lst_config_lst1_TOP",
                "#/components/schemas/toaster2_lst_config_lst1");
        verifyRequestRef(jsonNodeLst1.get(), "#/components/schemas/toaster2_lst_lst1_TOP",
                "#/components/schemas/toaster2_lst_lst1_TOP");

        final var jsonNodeMakeToast = doc.paths().get("/rests/operations/toaster2:make-toast");
        assertNull(jsonNodeMakeToast.get());
        verifyRequestRef(jsonNodeMakeToast.post(), "#/components/schemas/toaster2_make-toast_input_TOP",
                "#/components/schemas/toaster2_make-toast_input");

        final var jsonNodeCancelToast = doc.paths().get("/rests/operations/toaster2:cancel-toast");
        assertNull(jsonNodeCancelToast.get());
        // Test RPC with empty input
        final var postContent = jsonNodeCancelToast.post().get("requestBody").get("content");
        final var jsonSchema = postContent.get("application/json").get("schema");
        assertNull(jsonSchema.get("$ref"));
        assertEquals(2, jsonSchema.size());
        final var xmlSchema = postContent.get("application/xml").get("schema");
        assertNull(xmlSchema.get("$ref"));
        assertEquals(2, xmlSchema.size());

        // Test `components/schemas` objects
        final var definitions = doc.components().schemas();
        assertEquals(44, definitions.size());
    }

    /**
     *  Test JSON and XML references for request operation.
     */
    private static void verifyRequestRef(final JsonNode path, final String expectedJsonRef,
            final String expectedXmlRef) {
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
