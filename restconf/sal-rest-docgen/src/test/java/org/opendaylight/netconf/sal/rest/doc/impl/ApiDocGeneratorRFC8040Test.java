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
    public void testSwaggerObjectsObjects() {
        final var module = CONTEXT.findModule(MY_YANG, Revision.of(MY_YANG_REVISION)).orElseThrow();
        final SwaggerObject doc = generator.getSwaggerDocSpec(module, "http", "localhost:8181", "/", "", CONTEXT,
                OAversion.V3_0);
        assertEquals(List.of("/rests/data", "/rests/data/my-yang:data"),
                ImmutableList.copyOf(doc.getPaths().fieldNames()));
        final var myYangData = doc.getPaths().get("/rests/data/my-yang:data");

        // Test JSON and XML references for POST operation
        final var post = myYangData.get("post");
        final var postRequestBody = post.get("requestBody");
        final var postContent = postRequestBody.get("content");
        final var postJson = postContent.get("application/json");
        final var postJsonSchema = postJson.get("schema");
        final var postJsonRef = postJsonSchema.get("$ref");
        assertEquals("#/components/schemas/my-yang_config_data", postJsonRef.textValue());
        final var postXml = postContent.get("application/xml");
        final var postXmlSchema = postXml.get("schema");
        final var postXmlRef = postXmlSchema.get("$ref");
        assertEquals("#/components/schemas/my-yang_config_data", postXmlRef.textValue());

        // Test JSON and XML references for PUT operation
        final var put = myYangData.get("put");
        final var putRequestBody = put.get("requestBody");
        final var putContent = putRequestBody.get("content");
        final var putJson = putContent.get("application/json");
        final var putJsonSchema = putJson.get("schema");
        final var putJsonRef = putJsonSchema.get("$ref");
        assertEquals("#/components/schemas/my-yang_config_data_TOP", putJsonRef.textValue());
        final var putXml = putContent.get("application/xml");
        final var putXmlSchema = putXml.get("schema");
        final var putXmlRef = putXmlSchema.get("$ref");
        assertEquals("#/components/schemas/my-yang_config_data", putXmlRef.textValue());

        ObjectNode definitions = doc.getDefinitions();
        assertEquals(5, definitions.size());
        definitions.has("my-yang_config_data");
        definitions.has("my-yang_config_data_TOP");
        definitions.has("my-yang_data");
        definitions.has("my-yang_data_TOP");
        definitions.has("my-yang_module");
    }
}
