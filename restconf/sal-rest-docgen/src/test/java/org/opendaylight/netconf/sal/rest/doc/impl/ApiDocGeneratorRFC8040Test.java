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
import java.util.List;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.yang.common.Revision;

public final class ApiDocGeneratorRFC8040Test extends AbstractApiDocTest {
    private static final String NAME = "toaster2";
    private static final String MY_YANG = "my-yang";
    private static final String MY_YANG_REVISION = "2022-10-06";
    private static final String REVISION_DATE = "2009-11-20";
    private static final String NAME_2 = "toaster";
    private static final String REVISION_DATE_2 = "2009-11-20";

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
     * Test that generated configuration paths allow to use operations: get, put, delete and post.
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
    public void testSimpleSwaggerObjects() {
        final var module = CONTEXT.findModule(MY_YANG, Revision.of(MY_YANG_REVISION)).orElseThrow();
        final SwaggerObject doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
                OAversion.V3_0);
        assertEquals(List.of("/rests/data", "/rests/data/my-yang:data"),
                ImmutableList.copyOf(doc.getPaths().fieldNames()));
        final var myYangData = doc.getPaths().get("/rests/data/my-yang:data");
        verifyPostRef(myYangData, "#/components/schemas/my-yang_config_data",
                "#/components/schemas/my-yang_config_data");
        verifyPutRef(myYangData, "#/components/schemas/my-yang_config_data_TOP",
                "#/components/schemas/my-yang_config_data");
        // TODO: The XML should point to the "my-yang_data" element instead of the "TOP" element.
        verifyGetRef(myYangData, "#/components/schemas/my-yang_data_TOP",
                "#/components/schemas/my-yang_data_TOP");

        // Test `components/schemas` objects
        final var definitions = doc.getDefinitions();
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
        final SwaggerObject doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
                OAversion.V3_0);

        final var toaster = doc.getPaths().get("/rests/data/toaster2:toaster");
        verifyPostRef(toaster, "#/components/schemas/toaster2_config_toaster",
                "#/components/schemas/toaster2_config_toaster");
        verifyPutRef(toaster, "#/components/schemas/toaster2_config_toaster_TOP",
                "#/components/schemas/toaster2_config_toaster");
        verifyGetRef(toaster, "#/components/schemas/toaster2_toaster_TOP",
                "#/components/schemas/toaster2_toaster_TOP");

        final var toasterSlot = doc.getPaths().get("/rests/data/toaster2:toaster/toasterSlot={slotId}");
        verifyPostRef(toasterSlot, "#/components/schemas/toaster2_toaster_config_toasterSlot",
                "#/components/schemas/toaster2_toaster_config_toasterSlot");
        verifyPutRef(toasterSlot, "#/components/schemas/toaster2_toaster_config_toasterSlot_TOP",
                "#/components/schemas/toaster2_toaster_config_toasterSlot");
        verifyGetRef(toasterSlot, "#/components/schemas/toaster2_toaster_toasterSlot_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_TOP");

        final var slotInfo = doc.getPaths().get(
                "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo");
        verifyPostRef(slotInfo, "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo",
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo");
        verifyPutRef(slotInfo, "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo");
        verifyGetRef(slotInfo, "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo_TOP");

        final var lst = doc.getPaths().get("/rests/data/toaster2:lst");
        verifyPostRef(lst, "#/components/schemas/toaster2_config_lst",
                "#/components/schemas/toaster2_config_lst");
        verifyPutRef(lst, "#/components/schemas/toaster2_config_lst_TOP",
                "#/components/schemas/toaster2_config_lst");
        verifyGetRef(lst, "#/components/schemas/toaster2_lst_TOP",
                "#/components/schemas/toaster2_lst_TOP");

        final var lst1 = doc.getPaths().get("/rests/data/toaster2:lst/lst1={key1},{key2}");
        verifyPostRef(lst1, "#/components/schemas/toaster2_lst_config_lst1",
                "#/components/schemas/toaster2_lst_config_lst1");
        verifyPutRef(lst1, "#/components/schemas/toaster2_lst_config_lst1_TOP",
                "#/components/schemas/toaster2_lst_config_lst1");
        verifyGetRef(lst1, "#/components/schemas/toaster2_lst_lst1_TOP",
                "#/components/schemas/toaster2_lst_lst1_TOP");

        final var makeToast = doc.getPaths().get("/rests/operations/toaster2:make-toast");
        // TODO: The RPC only contains a `POST` example, so the `GET` request is missing here.
        assertEquals(1, makeToast.size());
        verifyPostRef(makeToast, "#/components/schemas/toaster2_make-toast_input_TOP",
                "#/components/schemas/toaster2_make-toast_input");

        final var cancelToast = doc.getPaths().get("/rests/operations/toaster2:cancel-toast");
        assertEquals(1, cancelToast.size());
        // TODO: For some reason, this RPC does not contain a reference but instead contains a specific object.
        //       It should be replaced with a reference.
        final var postContent = cancelToast.get("post").get("requestBody").get("content");
        final var jsonSchema = postContent.get("application/json").get("schema");
        assertNull(jsonSchema.get("$ref"));
        assertEquals(2, jsonSchema.size());
        final var xmlSchema = postContent.get("application/xml").get("schema");
        assertNull(xmlSchema.get("$ref"));
        assertEquals(2, xmlSchema.size());

        // Test `components/schemas` objects
        final var definitions = doc.getDefinitions();
        assertEquals(44, definitions.size());
    }

    /**
     *  Test JSON and XML references for POST operation.
     */
    public void verifyPostRef(final JsonNode path, final String expectedJsonRef, final String expectedXmlRef) {
        final var postContent = path.get("post").get("requestBody").get("content");
        assertNotNull(postContent);
        final var postJsonRef = postContent.get("application/json").get("schema").get("$ref");
        assertNotNull(postJsonRef);
        assertEquals(expectedJsonRef, postJsonRef.textValue());
        final var postXmlRef = postContent.get("application/xml").get("schema").get("$ref");
        assertNotNull(postXmlRef);
        assertEquals(expectedXmlRef, postXmlRef.textValue());
    }

    /**
     * Test JSON and XML references for PUT operation.
     */
    public void verifyPutRef(final JsonNode path, final String expectedJsonRef, final String expectedXmlRef) {
        final var putContent = path.get("put").get("requestBody").get("content");
        assertNotNull(putContent);
        final var putJsonRef =  putContent.get("application/json").get("schema").get("$ref");
        assertNotNull(putJsonRef);
        assertEquals(expectedJsonRef, putJsonRef.textValue());
        final var putXmlRef = putContent.get("application/xml").get("schema").get("$ref");
        assertNotNull(putXmlRef);
        assertEquals(expectedXmlRef, putXmlRef.textValue());
    }

    /**
     * Test JSON and XML references for GET operation.
     */
    public void verifyGetRef(final JsonNode path, final String expectedJsonRef, final String expectedXmlRef) {
        final var getContent = path.get("get").get("responses").get("200").get("content");
        assertNotNull(getContent);
        final var getJsonRef = getContent.get("application/json").get("schema").get("$ref");
        assertNotNull(getJsonRef);
        assertEquals(expectedJsonRef, getJsonRef.textValue());
        final var getXmlRef = getContent.get("application/xml").get("schema").get("$ref");
        assertNotNull(getXmlRef);
        assertEquals(expectedXmlRef, getXmlRef.textValue());
    }
}
