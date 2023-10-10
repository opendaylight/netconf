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
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.COMPONENTS_PREFIX;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.ws.rs.core.UriInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public final class OpenApiGeneratorRFC8040Test {
    private static final String TOASTER_2 = "toaster2";
    private static final String REVISION_DATE = "2009-11-20";
    private static final String MANDATORY_TEST = "mandatory-test";
    private static final String CONFIG_ROOT_CONTAINER = "mandatory-test_config_root-container";
    private static final String ROOT_CONTAINER = "mandatory-test_root-container";
    private static final String CONFIG_MANDATORY_CONTAINER = "mandatory-test_root-container_config_mandatory-container";
    private static final String MANDATORY_CONTAINER = "mandatory-test_root-container_mandatory-container";
    private static final String CONFIG_MANDATORY_LIST = "mandatory-test_root-container_config_mandatory-list";
    private static final String MANDATORY_LIST = "mandatory-test_root-container_mandatory-list";
    private static final String MANDATORY_TEST_MODULE = "mandatory-test_module";
    private static final String CONTAINER = "container";
    private static final String LIST = "list";

    private static DOMSchemaService schemaService;
    private static UriInfo uriInfo;

    private final OpenApiGeneratorRFC8040 generator = new OpenApiGeneratorRFC8040(schemaService);

    @BeforeClass
    public static void beforeClass() throws Exception {
        schemaService = mock(DOMSchemaService.class);
        final EffectiveModelContext context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        when(schemaService.getGlobalContext()).thenReturn(context);
        uriInfo = DocGenTestHelper.createMockUriInfo("http://localhost/path");
    }

    /**
     * Test that paths are generated according to the model.
     */
    @Test
    public void testPaths() {
        final OpenApiObject doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

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

        final OpenApiObject doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

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
        final OpenApiObject doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

        final Map<String, Schema> schemas = doc.components().schemas();
        assertNotNull(schemas);

        final Schema configLstTop = schemas.get("toaster2_config_lst_TOP");
        assertNotNull(configLstTop);
        DocGenTestHelper.containsReferences(configLstTop, "toaster2:lst", "#/components/schemas/toaster2_config_lst");

        final Schema configLst = schemas.get("toaster2_config_lst");
        assertNotNull(configLst);
        DocGenTestHelper.containsReferences(configLst, "lst1", "#/components/schemas/toaster2_lst_config_lst1");
        DocGenTestHelper.containsReferences(configLst, "cont1", "#/components/schemas/toaster2_lst_config_cont1");

        final Schema configLst1Top = schemas.get("toaster2_lst_config_lst1_TOP");
        assertNotNull(configLst1Top);
        DocGenTestHelper.containsReferences(configLst1Top, "toaster2:lst1",
            "#/components/schemas/toaster2_lst_config_lst1");

        final Schema configLst1 = schemas.get("toaster2_lst_config_lst1");
        assertNotNull(configLst1);

        final Schema configCont1Top = schemas.get("toaster2_lst_config_cont1_TOP");
        assertNotNull(configCont1Top);
        DocGenTestHelper.containsReferences(configCont1Top, "toaster2:cont1",
            "#/components/schemas/toaster2_lst_config_cont1");

        final Schema configCont1 = schemas.get("toaster2_lst_config_cont1");
        assertNotNull(configCont1);
        DocGenTestHelper.containsReferences(configCont1, "cont11",
            "#/components/schemas/toaster2_lst_cont1_config_cont11");
        DocGenTestHelper.containsReferences(configCont1, "lst11",
            "#/components/schemas/toaster2_lst_cont1_config_lst11");

        final Schema configCont11Top = schemas.get("toaster2_lst_cont1_config_cont11_TOP");
        assertNotNull(configCont11Top);
        DocGenTestHelper.containsReferences(configCont11Top, "toaster2:cont11",
            "#/components/schemas/toaster2_lst_cont1_config_cont11");

        final Schema configCont11 = schemas.get("toaster2_lst_cont1_config_cont11");
        assertNotNull(configCont11);

        final Schema configLst11Top = schemas.get("toaster2_lst_cont1_config_lst11_TOP");
        assertNotNull(configLst11Top);
        DocGenTestHelper.containsReferences(configLst11Top, "toaster2:lst11",
            "#/components/schemas/toaster2_lst_cont1_config_lst11");

        final Schema configLst11 = schemas.get("toaster2_lst_cont1_config_lst11");
        assertNotNull(configLst11);
    }

    /**
     * Test that reference to schema in each path is valid (all referenced schemas exist).
     */
    @Test
    public void testSchemasExistenceSingleModule() {
        final var document = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);
        assertNotNull(document);
        final var referencedSchemas = new HashSet<String>();
        for (final var path : document.paths().values()) {
            referencedSchemas.addAll(extractSchemaRefFromPath(path));
        }
        final var schemaNames = document.components().schemas().keySet();
        for (final var ref : referencedSchemas) {
            assertTrue("Referenced schema " + ref + " does not exist", schemaNames.contains(ref));
        }
    }

    /**
     * Test that generated document contains RPC schemas for "make-toast" with correct input.
     */
    @Test
    public void testRPC() {
        final OpenApiObject doc = generator.getApiDeclaration("toaster", "2009-11-20", uriInfo);
        assertNotNull(doc);

        final Map<String, Schema> schemas = doc.components().schemas();
        final Schema inputTop = schemas.get("toaster_make-toast_input_TOP");
        assertNotNull(inputTop);
        final String testString = "{\"toaster:input\":{\"$ref\":\"#/components/schemas/toaster_make-toast_input\"}}";
        assertEquals(testString, inputTop.properties().toString());
        final Schema input = schemas.get("toaster_make-toast_input");
        final JsonNode properties = input.properties();
        assertTrue(properties.has("toasterDoneness"));
        assertTrue(properties.has("toasterToastType"));
    }

    @Test
    public void testChoice() {
        final var doc = generator.getApiDeclaration("choice-test", null, uriInfo);
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

    @Test
    public void testMandatory() {
        final var doc = generator.getApiDeclaration(MANDATORY_TEST, null, uriInfo);
        assertNotNull(doc);
        final var schemas = doc.components().schemas();
        final var containersWithRequired = new ArrayList<String>();

        final var reqRootContainerElements = Set.of("mandatory-root-leaf", "mandatory-container",
            "mandatory-first-choice", "mandatory-list");
        verifyRequiredField(schemas.get(CONFIG_ROOT_CONTAINER), reqRootContainerElements);
        containersWithRequired.add(CONFIG_ROOT_CONTAINER);
        verifyRequiredField(schemas.get(ROOT_CONTAINER), reqRootContainerElements);
        containersWithRequired.add(ROOT_CONTAINER);

        final var reqMandatoryContainerElements = Set.of("mandatory-leaf", "leaf-list-with-min-elements");
        verifyRequiredField(schemas.get(CONFIG_MANDATORY_CONTAINER), reqMandatoryContainerElements);
        containersWithRequired.add(CONFIG_MANDATORY_CONTAINER);
        verifyRequiredField(schemas.get(MANDATORY_CONTAINER), reqMandatoryContainerElements);
        containersWithRequired.add(MANDATORY_CONTAINER);

        final var reqMandatoryListElements = Set.of("mandatory-list-field");
        verifyRequiredField(schemas.get(CONFIG_MANDATORY_LIST), reqMandatoryListElements);
        containersWithRequired.add(CONFIG_MANDATORY_LIST);
        verifyRequiredField(schemas.get(MANDATORY_LIST), reqMandatoryListElements);
        containersWithRequired.add(MANDATORY_LIST);

        final var testModuleMandatoryArray = Set.of("root-container", "root-mandatory-list");
        verifyRequiredField(schemas.get(MANDATORY_TEST_MODULE), testModuleMandatoryArray);
        containersWithRequired.add(MANDATORY_TEST_MODULE);

        verifyThatOthersNodeDoesNotHaveRequiredField(containersWithRequired, schemas);
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

        final var doc = generator.getApiDeclaration("recursive", "2023-05-22", uriInfo);
        assertNotNull(doc);

        final var paths = doc.paths();
        assertEquals(5, paths.size());

        for (final var expectedPath : configPaths.entrySet()) {
            assertTrue(paths.containsKey(expectedPath.getKey()));
            final int expectedSize = expectedPath.getValue();

            final var path = paths.get(expectedPath.getKey());

            final var get = path.get();
            assertNotNull(get);
            assertEquals(expectedSize + 1, get.parameters().size());

            final var put = path.put();
            assertNotNull(put);
            assertEquals(expectedSize, put.parameters().size());

            final var delete = path.delete();
            assertNotNull(delete);
            assertEquals(expectedSize, delete.parameters().size());

            final var post = path.post();
            assertNotNull(post);
            assertEquals(expectedSize, post.parameters().size());

            final var patch = path.patch();
            assertNotNull(patch);
            assertEquals(expectedSize, patch.parameters().size());
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
        final var doc = generator.getApiDeclaration("path-params-test", null, uriInfo);

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

    /**
     * Test that request parameters are correctly typed.
     */
    @Test
    public void testParametersTypes() {
        final var doc = generator.getApiDeclaration("typed-params", "2023-10-24", uriInfo);
        final var pathToContainer = "/rests/data/typed-params:typed/";
        final var integerTypes = List.of("uint64", "uint32", "uint16", "uint8", "int64", "int32", "int16", "int8");
        for (final var type: integerTypes) {
            final var typeKey = type + "-key";
            final var path = pathToContainer + type + "={" + typeKey + "}";
            assertTrue(doc.paths().containsKey(path));
            assertEquals("integer", doc.paths().get(path).get().parameters().get(0).get("schema").get("type")
                .textValue());
        }
    }

    /**
     * Test that request for actions is correct and has parameters.
     */
    @Test
    public void testActionPathsParams() {
        final var doc = generator.getApiDeclaration("action-types", null, uriInfo);

        final var pathWithParameters = "/rests/operations/action-types:list={name}/list-action";
        assertTrue(doc.paths().containsKey(pathWithParameters));
        assertEquals(List.of("name"), getPathParameters(doc.paths(), pathWithParameters));

        final var pathWithoutParameters = "/rests/operations/action-types:multi-container/inner-container/action";
        assertTrue(doc.paths().containsKey(pathWithoutParameters));
        assertEquals(List.of(), getPathParameters(doc.paths(), pathWithoutParameters));
    }

    @Test
    public void testSimpleOpenApiObjects() {
        final var doc = generator.getApiDeclaration("my-yang", "2022-10-06", uriInfo);

        assertEquals(Set.of("/rests/data", "/rests/data/my-yang:data"), doc.paths().keySet());
        final var JsonNodeMyYangData = doc.paths().get("/rests/data/my-yang:data");
        verifyRequestRef(JsonNodeMyYangData.post(), "#/components/schemas/my-yang_config_data",
                "#/components/schemas/my-yang_config_data");
        verifyRequestRef(JsonNodeMyYangData.put(), "#/components/schemas/my-yang_config_data_TOP",
                "#/components/schemas/my-yang_config_data");
        verifyRequestRef(JsonNodeMyYangData.get(), "#/components/schemas/my-yang_data_TOP",
                "#/components/schemas/my-yang_data");

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
        final var doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

        final var jsonNodeToaster = doc.paths().get("/rests/data/toaster2:toaster");
        verifyPostRequestRef(jsonNodeToaster.post(), "#/components/schemas/toaster2_toaster_config_toasterSlot",
                "#/components/schemas/toaster2_toaster_config_toasterSlot", LIST);
        verifyRequestRef(jsonNodeToaster.put(), "#/components/schemas/toaster2_config_toaster_TOP",
                "#/components/schemas/toaster2_config_toaster");
        verifyRequestRef(jsonNodeToaster.get(), "#/components/schemas/toaster2_toaster_TOP",
                "#/components/schemas/toaster2_toaster");

        final var jsonNodeToasterSlot = doc.paths().get("/rests/data/toaster2:toaster/toasterSlot={slotId}");
        verifyPostRequestRef(jsonNodeToasterSlot.post(), "#/components/schemas/toaster2_toaster_"
                + "toasterSlot_config_slotInfo", "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo",
            CONTAINER);
        verifyRequestRef(jsonNodeToasterSlot.put(), "#/components/schemas/toaster2_toaster_config_toasterSlot_TOP",
                "#/components/schemas/toaster2_toaster_config_toasterSlot");
        verifyRequestRef(jsonNodeToasterSlot.get(), "#/components/schemas/toaster2_toaster_toasterSlot_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot");

        final var jsonNodeSlotInfo = doc.paths().get(
                "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.post(),
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo",
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.put(),
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_config_slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.get(), "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo_TOP",
                "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo");

        final var jsonNodeLst = doc.paths().get("/rests/data/toaster2:lst");
        verifyPostRequestRef(jsonNodeLst.post(), "#/components/schemas/toaster2_lst_config_cont1",
                "#/components/schemas/toaster2_lst_config_cont1", CONTAINER);
        verifyRequestRef(jsonNodeLst.put(), "#/components/schemas/toaster2_config_lst_TOP",
                "#/components/schemas/toaster2_config_lst");
        verifyRequestRef(jsonNodeLst.get(), "#/components/schemas/toaster2_lst_TOP",
                "#/components/schemas/toaster2_lst");

        final var jsonNodeLst1 = doc.paths().get("/rests/data/toaster2:lst/lst1={key1},{key2}");
        verifyRequestRef(jsonNodeLst1.post(), "#/components/schemas/toaster2_lst_config_lst1",
                "#/components/schemas/toaster2_lst_config_lst1");
        verifyRequestRef(jsonNodeLst1.put(), "#/components/schemas/toaster2_lst_config_lst1_TOP",
                "#/components/schemas/toaster2_lst_config_lst1");
        verifyRequestRef(jsonNodeLst1.get(), "#/components/schemas/toaster2_lst_lst1_TOP",
                "#/components/schemas/toaster2_lst_lst1");

        final var jsonNodeMakeToast = doc.paths().get("/rests/operations/toaster2:make-toast");
        assertNull(jsonNodeMakeToast.get());
        verifyRequestRef(jsonNodeMakeToast.post(), "#/components/schemas/toaster2_make-toast_input_TOP",
                "#/components/schemas/toaster2_make-toast_input");

        final var jsonNodeCancelToast = doc.paths().get("/rests/operations/toaster2:cancel-toast");
        assertNull(jsonNodeCancelToast.get());
        // Test RPC with empty input
        final var postContent = jsonNodeCancelToast.post().requestBody().get("content");
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
     * Test that checks if securitySchemes and security elements are present.
     */
    @Test
    public void testAuthenticationFeature() {
        final var doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

        assertEquals("[{\"basicAuth\":[]}]", doc.security().toString());
        assertEquals("{\"type\":\"http\",\"scheme\":\"basic\"}",
            doc.components().securitySchemes().basicAuth().toString());
    }

    /**
     * Test that checks if namespace for rpc is present.
     */
    @Test
    public void testRpcNamespace() {
        final var doc = generator.getApiDeclaration("toaster", "2009-11-20", uriInfo);
        assertNotNull("Failed to find Datastore API", doc);
        final var paths = doc.paths();
        final var path = paths.get("/rests/operations/toaster:cancel-toast");
        assertNotNull(path);
        final var content = path.post().requestBody().get("content").get("application/xml");
        assertNotNull(content);
        final var schema = content.get("schema");
        assertNotNull(schema);
        final var xml = schema.get("xml");
        assertNotNull(xml);
        final var namespace = xml.get("namespace");
        assertNotNull(namespace);
        assertEquals("http://netconfcentral.org/ns/toaster", namespace.asText());
    }

    /**
     * Test that checks if namespace for actions is present.
     */
    @Test
    public void testActionsNamespace() {
        final var doc = generator.getApiDeclaration("action-types", null, uriInfo);
        assertNotNull("Failed to find Datastore API", doc);
        final var paths = doc.paths();
        final var path = paths.get("/rests/operations/action-types:multi-container/inner-container/action");
        assertNotNull(path);
        final var content = path.post().requestBody().get("content").get("application/xml");
        assertNotNull(content);
        final var schema = content.get("schema");
        assertNotNull(schema);
        final var xml = schema.get("xml");
        assertNotNull(xml);
        final var namespace = xml.get("namespace");
        assertNotNull(namespace);
        assertEquals("urn:ietf:params:xml:ns:yang:test:action:types", namespace.asText());
    }

    /**
     *  Test JSON and XML references for request operation.
     */
    private static void verifyRequestRef(final Operation operation, final String expectedJsonRef,
            final String expectedXmlRef) {
        final JsonNode postContent;
        if (operation.requestBody() != null) {
            postContent = operation.requestBody().get("content");
        } else {
            postContent = operation.responses().get("200").get("content");
        }
        assertNotNull(postContent);
        final var postJsonRef = postContent.get("application/json").get("schema").get("$ref");
        assertNotNull(postJsonRef);
        assertEquals(expectedJsonRef, postJsonRef.textValue());
        final var postXmlRef = postContent.get("application/xml").get("schema").get("$ref");
        assertNotNull(postXmlRef);
        assertEquals(expectedXmlRef, postXmlRef.textValue());
    }

    private static void verifyPostRequestRef(final Operation operation, final String expectedJsonRef,
        final String expectedXmlRef, String nodeType) {
        final JsonNode postContent;
        if (operation.requestBody() != null) {
            postContent = operation.requestBody().get("content");
        } else {
            postContent = operation.responses().get("200").get("content");
        }
        assertNotNull(postContent);
        final String postJsonRef;
        if (nodeType.equals(CONTAINER)) {
            postJsonRef = postContent.path("application/json").path("schema").path("properties").elements().next()
                .path("$ref").textValue();
        } else {
            postJsonRef = postContent.path("application/json").path("schema").path("properties").elements().next()
                .path("items").path("$ref").textValue();
        }
        assertEquals(expectedJsonRef, postJsonRef);
        final var postXmlRef = postContent.get("application/xml").get("schema").get("$ref");
        assertNotNull(postXmlRef);
        assertEquals(expectedXmlRef, postXmlRef.textValue());
    }


    private static void verifyThatOthersNodeDoesNotHaveRequiredField(final List<String> expected,
            final Map<String, Schema> schemas) {
        for (final var schema : schemas.entrySet()) {
            if (expected.contains(schema.getKey())) {
                continue;
            }
            assertNull("Json node " + schema.getKey() + " should not have 'required' field in body",
                schema.getValue().required());
        }
    }

    private static void verifyRequiredField(final Schema rootContainer, final Set<String> expected) {
        assertNotNull(rootContainer);
        final var required = rootContainer.required();
        assertNotNull(required);
        assertTrue(required.isArray());
        final var actualContainerArray = StreamSupport.stream(required.spliterator(), false)
            .map(JsonNode::textValue)
            .collect(Collectors.toSet());
        assertEquals(expected, actualContainerArray);
    }

    private static Set<String> extractSchemaRefFromPath(final Path path) {
        if (path == null) {
            return Set.of();
        }
        final var references = new HashSet<String>();
        final var get = path.get();
        if (get != null) {
            references.addAll(schemaRefFromContent(get.responses().path("200").path("content")));
        }
        final var post = path.post();
        if (post != null) {
            references.addAll(schemaRefFromContent(post.requestBody().path("content")));
        }
        final var put = path.put();
        if (put != null) {
            references.addAll(schemaRefFromContent(put.requestBody().path("content")));
        }
        final var patch = path.patch();
        if (patch != null) {
            references.addAll(schemaRefFromContent(patch.requestBody().path("content")));
        }
        return references;
    }

    /**
     * The schema node does not have 1 specific structure and the "$ref" child is not always the first child after
     * schema. Possible schema structures include:
     * <ul>
     *   <li>schema/$ref/{reference}</li>
     *   <li>schema/properties/{nodeName}/$ref/{reference}</li>
     *   <li>schema/properties/{nodeName}/items/$ref/{reference}</li>
     * </ul>
     * @param content the element identified with key "content"
     * @return the set of referenced schemas
     */
    private static Set<String> schemaRefFromContent(final JsonNode content) {
        final HashSet<String> refs = new HashSet<>();
        content.fieldNames().forEachRemaining(mediaType -> {
            final JsonNode schema = content.path(mediaType).path("schema");
            final JsonNode props = schema.path("properties");
            final JsonNode nameNode = props.isMissingNode() ? props : props.elements().next().path("items");
            final JsonNode ref;
            if (props.isMissingNode()) {
                // either there is no node with the key "properties", try to find immediate child of schema
                ref = schema.path("$ref");
            } else if (nameNode.path("items").isMissingNode()) {
                // or the "properties" is defined and under that we didn't find the "items" node
                // try to get "$ref" as immediate child under nameNode
                ref = nameNode.path("$ref");
            } else {
                // or the "items" node is defined, in which case we try to get the "$ref" from this node
                ref = nameNode.path("items").path("$ref");
            }

            if (ref != null && !ref.isMissingNode()) {
                refs.add(ref.asText().replaceFirst(COMPONENTS_PREFIX, ""));
            }
        });
        return refs;
    }
}
