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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.opendaylight.restconf.openapi.AbstractOpenApiTest;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.yangtools.yang.common.Revision;

public final class OpenApiGeneratorRFC8040Test extends AbstractOpenApiTest {
    private static final String NAME = "toaster2";
    private static final String MY_YANG = "my-yang";
    private static final String MY_YANG_REVISION = "2022-10-06";
    private static final String REVISION_DATE = "2009-11-20";
    private static final String NAME_2 = "toaster";
    private static final String REVISION_DATE_2 = "2009-11-20";
    private static final String CHOICE_TEST_MODULE = "choice-test";
    private static final String PROPERTIES = "properties";
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

        final ObjectNode schemas = doc.getComponents().getSchemas();
        assertNotNull(schemas);

        final JsonNode configLstTop = schemas.get("toaster2_config_lst_TOP");
        assertNotNull(configLstTop);
        DocGenTestHelper.containsReferences(configLstTop, "lst", "#/components/schemas/toaster2_config_lst");

        final JsonNode configLst = schemas.get("toaster2_config_lst");
        assertNotNull(configLst);
        DocGenTestHelper.containsReferences(configLst, "lst1", "#/components/schemas/toaster2_lst_config_lst1");
        DocGenTestHelper.containsReferences(configLst, "cont1", "#/components/schemas/toaster2_lst_config_cont1");

        final JsonNode configLst1Top = schemas.get("toaster2_lst_config_lst1_TOP");
        assertNotNull(configLst1Top);
        DocGenTestHelper.containsReferences(configLst1Top, "lst1", "#/components/schemas/toaster2_lst_config_lst1");

        final JsonNode configLst1 = schemas.get("toaster2_lst_config_lst1");
        assertNotNull(configLst1);

        final JsonNode configCont1Top = schemas.get("toaster2_lst_config_cont1_TOP");
        assertNotNull(configCont1Top);
        DocGenTestHelper.containsReferences(configCont1Top, "cont1", "#/components/schemas/toaster2_lst_config_cont1");

        final JsonNode configCont1 = schemas.get("toaster2_lst_config_cont1");
        assertNotNull(configCont1);
        DocGenTestHelper.containsReferences(configCont1, "cont11",
                "#/components/schemas/toaster2_lst_cont1_config_cont11");
        DocGenTestHelper.containsReferences(configCont1, "lst11",
                "#/components/schemas/toaster2_lst_cont1_config_lst11");

        final JsonNode configCont11Top = schemas.get("toaster2_lst_cont1_config_cont11_TOP");
        assertNotNull(configCont11Top);
        DocGenTestHelper.containsReferences(configCont11Top,
                "cont11", "#/components/schemas/toaster2_lst_cont1_config_cont11");

        final JsonNode configCont11 = schemas.get("toaster2_lst_cont1_config_cont11");
        assertNotNull(configCont11);

        final JsonNode configLst11Top = schemas.get("toaster2_lst_cont1_config_lst11_TOP");
        assertNotNull(configLst11Top);
        DocGenTestHelper.containsReferences(configLst11Top, "lst11",
                "#/components/schemas/toaster2_lst_cont1_config_lst11");

        final JsonNode configLst11 = schemas.get("toaster2_lst_cont1_config_lst11");
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

        final ObjectNode schemas = doc.getComponents().getSchemas();
        final JsonNode inputTop = schemas.get("toaster_make-toast_input_TOP");
        assertNotNull(inputTop);
        final String testString = "{\"input\":{\"$ref\":\"#/components/schemas/toaster_make-toast_input\"}}";
        assertEquals(testString, inputTop.get("properties").toString());
        final JsonNode input = schemas.get("toaster_make-toast_input");
        final JsonNode properties = input.get("properties");
        assertTrue(properties.has("toasterDoneness"));
        assertTrue(properties.has("toasterToastType"));
    }

    @Test
    public void testChoice() {
        final var module = CONTEXT.findModule(CHOICE_TEST_MODULE).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);
        assertNotNull(doc);

        final var schemas = doc.getComponents().getSchemas();
        JsonNode firstContainer = schemas.get("choice-test_first-container");
        assertEquals("default-value",
                firstContainer.get(PROPERTIES).get("leaf-default").get("default").asText());
        assertFalse(firstContainer.get(PROPERTIES).has("leaf-non-default"));

        JsonNode secondContainer = schemas.get("choice-test_second-container");
        assertTrue(secondContainer.get(PROPERTIES).has("leaf-first-case"));
        assertFalse(secondContainer.get(PROPERTIES).has("leaf-second-case"));
    }

    @Test
    public void testSimpleSwaggerObjects() {
        final var module = CONTEXT.findModule(MY_YANG, Revision.of(MY_YANG_REVISION)).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        assertEquals(Set.of("/rests/data", "/rests/data/my-yang:data"), doc.getPaths().keySet());
        final var JsonNodeMyYangData = doc.getPaths().get("/rests/data/my-yang:data");
        verifyRequestRef(JsonNodeMyYangData.getPost(), "#/components/schemas/my-yang_config_data",
                "#/components/schemas/my-yang_config_data");
        verifyRequestRef(JsonNodeMyYangData.getPut(), "#/components/schemas/my-yang_config_data_TOP",
                "#/components/schemas/my-yang_config_data");
        // TODO: The XML should point to the "my-yang_data" element instead of the "TOP" element.
        verifyRequestRef(JsonNodeMyYangData.getGet(), "#/components/schemas/my-yang_data_TOP",
                "#/components/schemas/my-yang_data_TOP");

        // Test `components/schemas` objects
        final var definitions = doc.getComponents().getSchemas();
        assertEquals(5, definitions.size());
        assertTrue(definitions.has("my-yang_config_data"));
        assertTrue(definitions.has("my-yang_config_data_TOP"));
        assertTrue(definitions.has("my-yang_data"));
        assertTrue(definitions.has("my-yang_data_TOP"));
        assertTrue(definitions.has("my-yang_module"));
    }

    @Test
    public void testToaster2SwaggerObjects() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final var doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", CONTEXT);

        final var jsonNodeToaster = doc.getPaths().get("/rests/data/toaster2:toaster");
        verifyRequestRef(jsonNodeToaster.getPost(), "#/components/schemas/toaster2_config_toaster",
                "#/components/schemas/toaster2_config_toaster");
        verifyRequestRef(jsonNodeToaster.getPut(), "#/components/schemas/toaster2_config_toaster_TOP",
                "#/components/schemas/toaster2_config_toaster");
        verifyRequestRef(jsonNodeToaster.getGet(), "#/components/schemas/toaster2_toaster_TOP",
                "#/components/schemas/toaster2_toaster_TOP");

        final var jsonNodeToasterSlot = doc.getPaths().get("/rests/data/toaster2:toaster/toasterSlot={slotId}");
        verifyRequestRef(jsonNodeToasterSlot.getPost(), "#/components/schemas/toaster2_toaster_config_toasterSlot",
                "#/components/schemas/toaster2_toaster_config_toasterSlot");
        verifyRequestRef(jsonNodeToasterSlot.getPut(), "#/components/schemas/toaster2_toaster_config_toasterSlot_TOP",
                "#/components/schemas/toaster2_toaster_config_toasterSlot");
        verifyRequestRef(jsonNodeToasterSlot.getGet(), "#/components/schemas/toaster2_toaster_toasterSlot_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_TOP");

        final var jsonNodeSlotInfo = doc.getPaths().get(
                "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.getPost(),
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo",
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.getPut(),
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.getGet(), "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo_TOP");

        final var jsonNodeLst = doc.getPaths().get("/rests/data/toaster2:lst");
        verifyRequestRef(jsonNodeLst.getPost(), "#/components/schemas/toaster2_config_lst",
                "#/components/schemas/toaster2_config_lst");
        verifyRequestRef(jsonNodeLst.getPut(), "#/components/schemas/toaster2_config_lst_TOP",
                "#/components/schemas/toaster2_config_lst");
        verifyRequestRef(jsonNodeLst.getGet(), "#/components/schemas/toaster2_lst_TOP",
                "#/components/schemas/toaster2_lst_TOP");

        final var jsonNodeLst1 = doc.getPaths().get("/rests/data/toaster2:lst/lst1={key1},{key2}");
        verifyRequestRef(jsonNodeLst1.getPost(), "#/components/schemas/toaster2_lst_config_lst1",
                "#/components/schemas/toaster2_lst_config_lst1");
        verifyRequestRef(jsonNodeLst1.getPut(), "#/components/schemas/toaster2_lst_config_lst1_TOP",
                "#/components/schemas/toaster2_lst_config_lst1");
        verifyRequestRef(jsonNodeLst1.getGet(), "#/components/schemas/toaster2_lst_lst1_TOP",
                "#/components/schemas/toaster2_lst_lst1_TOP");

        final var jsonNodeMakeToast = doc.getPaths().get("/rests/operations/toaster2:make-toast");
        // TODO: The RPC only contains a `POST` example, so the `GET` request is missing here.
        assertNull(jsonNodeMakeToast.getGet());
        verifyRequestRef(jsonNodeMakeToast.getPost(), "#/components/schemas/toaster2_make-toast_input_TOP",
                "#/components/schemas/toaster2_make-toast_input");

        final var jsonNodeCancelToast = doc.getPaths().get("/rests/operations/toaster2:cancel-toast");
        assertNull(jsonNodeCancelToast.getGet());
        // TODO: For some reason, this RPC does not contain a reference but instead contains a specific object.
        //       It should be replaced with a reference.
        final var postContent = jsonNodeCancelToast.getPost().get("requestBody").get("content");
        final var jsonSchema = postContent.get("application/json").get("schema");
        assertNull(jsonSchema.get("$ref"));
        assertEquals(2, jsonSchema.size());
        final var xmlSchema = postContent.get("application/xml").get("schema");
        assertNull(xmlSchema.get("$ref"));
        assertEquals(2, xmlSchema.size());

        // Test `components/schemas` objects
        final var definitions = doc.getComponents().getSchemas();
        assertEquals(44, definitions.size());
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
