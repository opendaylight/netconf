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

        final JsonNode configLst = definitions.get("toaster2_config_lst");
        assertNotNull(configLst);
        DocGenTestHelper.containsReferences(configLst, "lst1", "#/definitions/toaster2_lst_config_lst1");
        DocGenTestHelper.containsReferences(configLst, "cont1", "#/definitions/toaster2_lst_config_cont1");

        final JsonNode configLst1 = definitions.get("toaster2_lst_config_lst1");
        assertNotNull(configLst1);

        final JsonNode configCont1 = definitions.get("toaster2_lst_config_cont1");
        assertNotNull(configCont1);
        DocGenTestHelper.containsReferences(configCont1, "cont11", "#/definitions/toaster2_lst_cont1_config_cont11");
        DocGenTestHelper.containsReferences(configCont1, "lst11", "#/definitions/toaster2_lst_cont1_config_lst11");

        final JsonNode configCont11 = definitions.get("toaster2_lst_cont1_config_cont11");
        assertNotNull(configCont11);

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
        final JsonNode inputTop = definitions.get("toaster_make-toast_input");
        assertNotNull(inputTop);
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
        final var jsonNodeMyYangData = doc.getPaths().get("/rests/data/my-yang:data");
        final var myYangData = "#/components/schemas/my-yang_config_data";
        verifyPostRef(jsonNodeMyYangData, myYangData, myYangData);
        verifyPutRef(jsonNodeMyYangData, myYangData, myYangData);
        verifyGetRef(jsonNodeMyYangData, myYangData, myYangData);

        // Test `components/schemas` objects
        final var definitions = doc.getDefinitions();
        assertEquals(2, definitions.size());
        assertTrue(definitions.has("my-yang_config_data"));
        assertTrue(definitions.has("my-yang_module"));
    }

    @Test
    public void testToaster2SwaggerObjects() {
        final var module = CONTEXT.findModule(NAME, Revision.of(REVISION_DATE)).orElseThrow();
        final SwaggerObject doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
                OAversion.V3_0);

        final var jsonNodeToaster = doc.getPaths().get("/rests/data/toaster2:toaster");
        final var toaster2 = "#/components/schemas/toaster2_config_toaster";
        verifyPostRef(jsonNodeToaster, toaster2, toaster2);
        verifyPutRef(jsonNodeToaster, toaster2, toaster2);
        verifyGetRef(jsonNodeToaster, toaster2, toaster2);

        final var jsonNodeToasterSlot = doc.getPaths().get("/rests/data/toaster2:toaster/toasterSlot={slotId}");
        final var toasterSlot2 = "#/components/schemas/toaster2_toaster_config_toasterSlot";
        verifyPostRef(jsonNodeToasterSlot, toasterSlot2, toasterSlot2);
        verifyPutRef(jsonNodeToasterSlot, toasterSlot2, toasterSlot2);
        verifyGetRef(jsonNodeToasterSlot, toasterSlot2, toasterSlot2);

        final var jsonNodeSlotInfo = doc.getPaths().get(
                "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo");
        final var slotInfo = "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo";
        verifyPostRef(jsonNodeSlotInfo, slotInfo, slotInfo);
        verifyPutRef(jsonNodeSlotInfo, slotInfo, slotInfo);
        verifyGetRef(jsonNodeSlotInfo, slotInfo, slotInfo);

        final var jsonNodeLst = doc.getPaths().get("/rests/data/toaster2:lst");
        final var lst = "#/components/schemas/toaster2_config_lst";
        verifyPostRef(jsonNodeLst, lst, lst);
        verifyPutRef(jsonNodeLst, lst, lst);
        verifyGetRef(jsonNodeLst, lst, lst);

        final var jsonNodeLst1 = doc.getPaths().get("/rests/data/toaster2:lst/lst1={key1},{key2}");
        final var lst1 = "#/components/schemas/toaster2_lst_config_lst1";
        verifyPostRef(jsonNodeLst1, lst1, lst1);
        verifyPutRef(jsonNodeLst1, lst1, lst1);
        verifyGetRef(jsonNodeLst1, lst1, lst1);

        final var jsonNodeMakeToast = doc.getPaths().get("/rests/operations/toaster2:make-toast");
        // TODO: The RPC only contains a `POST` example, so the `GET` request is missing here.
        assertEquals(1, jsonNodeMakeToast.size());
        final var makeToast = "#/components/schemas/toaster2_make-toast_input";
        verifyPostRef(jsonNodeMakeToast, makeToast, makeToast);

        final var jsonNodeCancelToast = doc.getPaths().get("/rests/operations/toaster2:cancel-toast");
        assertEquals(1, jsonNodeCancelToast.size());
        // TODO: For some reason, this RPC does not contain a reference but instead contains a specific object.
        //       It should be replaced with a reference.
        final var postContent = jsonNodeCancelToast.get("post").get("requestBody").get("content");
        final var jsonSchema = postContent.get("application/json").get("schema");
        assertNull(jsonSchema.get("$ref"));
        assertEquals(2, jsonSchema.size());
        final var xmlSchema = postContent.get("application/xml").get("schema");
        assertNull(xmlSchema.get("$ref"));
        assertEquals(2, xmlSchema.size());

        // Test `components/schemas` objects
        final var definitions = doc.getDefinitions();
        assertEquals(18, definitions.size());
    }

    /**
     *  Test JSON and XML references for POST operation.
     */
    public void verifyPostRef(final JsonNode path, final String expectedJsonRef, final String expectedXmlRef) {
        final var postContent = path.get("post").get("requestBody").get("content");
        final var postJsonRef = postContent.get("application/json").get("schema").get("$ref");
        assertEquals(expectedJsonRef, postJsonRef.textValue());
        final var postXmlRef = postContent.get("application/xml").get("schema").get("$ref");
        assertEquals(expectedXmlRef, postXmlRef.textValue());
    }

    /**
     * Test JSON and XML references for PUT operation.
     */
    public void verifyPutRef(final JsonNode path, final String expectedJsonRef, final String expectedXmlRef) {
        final var putContent = path.get("put").get("requestBody").get("content");
        final var putJsonRef =  putContent.get("application/json").get("schema").get("$ref");
        assertEquals(expectedJsonRef, putJsonRef.textValue());
        final var putXmlRef = putContent.get("application/xml").get("schema").get("$ref");
        assertEquals(expectedXmlRef, putXmlRef.textValue());
    }

    /**
     * Test JSON and XML references for GET operation.
     */
    public void verifyGetRef(final JsonNode path, final String expectedJsonRef, final String expectedXmlRef) {
        final var getContent = path.get("get").get("responses").get("200").get("content");
        final var getJsonRef = getContent.get("application/json").get("schema").get("$ref");
        assertEquals(expectedJsonRef, getJsonRef.textValue());
        final var getXmlRef = getContent.get("application/xml").get("schema").get("$ref");
        assertEquals(expectedXmlRef, getXmlRef.textValue());
    }
}
