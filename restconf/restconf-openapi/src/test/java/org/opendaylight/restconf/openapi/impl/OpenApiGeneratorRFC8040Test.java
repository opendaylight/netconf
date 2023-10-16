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
import static org.opendaylight.restconf.openapi.OpenApiTestUtils.getPathGetParameters;
import static org.opendaylight.restconf.openapi.OpenApiTestUtils.getPathPostParameters;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.BASIC_AUTH_NAME;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.COMPONENTS_PREFIX;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.model.MediaTypeObject;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.restconf.openapi.model.Property;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public final class OpenApiGeneratorRFC8040Test {
    private static final String TOASTER_2 = "toaster2";
    private static final String REVISION_DATE = "2009-11-20";
    private static final String MANDATORY_TEST = "mandatory-test";
    private static final String CONFIG_ROOT_CONTAINER = "mandatory-test_root-container";
    private static final String CONFIG_MANDATORY_CONTAINER = "mandatory-test_root-container_mandatory-container";
    private static final String CONFIG_MANDATORY_LIST = "mandatory-test_root-container_mandatory-list";
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
            "/rests/data/toaster2:lst={lf1}",
            "/rests/data/toaster2:lst={lf1}/cont1",
            "/rests/data/toaster2:lst={lf1}/cont1/cont11",
            "/rests/data/toaster2:lst={lf1}/cont1/lst11={lf111}",
            "/rests/data/toaster2:lst={lf1}/lst1={key1},{key2}",
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
        final List<String> configPaths = List.of("/rests/data/toaster2:lst={lf1}",
                "/rests/data/toaster2:lst={lf1}/cont1",
                "/rests/data/toaster2:lst={lf1}/cont1/cont11",
                "/rests/data/toaster2:lst={lf1}/cont1/lst11={lf111}",
                "/rests/data/toaster2:lst={lf1}/lst1={key1},{key2}");
        final String configPathForPostCont = "/rests/data/toaster2:lst={lf1}/cont1";
        final String configPathForPostLeaf = "/rests/data/toaster2:lst={lf1}/cont1/cont11";

        final OpenApiObject doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

        for (final String path : configPaths) {
            final Path node = doc.paths().get(path);
            assertNotNull(node.get());
            assertNotNull(node.put());
            assertNotNull(node.delete());
            assertNotNull(node.patch());
        }

        final Path node = doc.paths().get(configPathForPostCont);
        assertNotNull(node.post());

        // Assert we do not generate post for container which contains only leafs.
        final Path nodeLeaf = doc.paths().get(configPathForPostLeaf);
        assertNull(nodeLeaf.post());
    }

    /**
     * Test that generated document contains the following schemas.
     */
    @Test
    public void testSchemas() {
        final OpenApiObject doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

        final Map<String, Schema> schemas = doc.components().schemas();
        assertNotNull(schemas);

        final Schema configLst = schemas.get("toaster2_lst");
        assertNotNull(configLst);
        DocGenTestHelper.containsReferences(configLst, "lst1", "#/components/schemas/toaster2_lst_lst1");
        DocGenTestHelper.containsReferences(configLst, "cont1", "#/components/schemas/toaster2_lst_cont1");

        final Schema configLst1 = schemas.get("toaster2_lst_lst1");
        assertNotNull(configLst1);

        final Schema configCont1 = schemas.get("toaster2_lst_cont1");
        assertNotNull(configCont1);
        DocGenTestHelper.containsReferences(configCont1, "cont11", "#/components/schemas/toaster2_lst_cont1_cont11");
        DocGenTestHelper.containsReferences(configCont1, "lst11", "#/components/schemas/toaster2_lst_cont1_lst11");

        final Schema configCont11 = schemas.get("toaster2_lst_cont1_cont11");
        assertNotNull(configCont11);

        final Schema configLst11 = schemas.get("toaster2_lst_cont1_lst11");
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
        final Schema input = schemas.get("toaster_make-toast_input");
        final Map<String, Property> properties = input.properties();
        assertTrue(properties.containsKey("toasterDoneness"));
        assertTrue(properties.containsKey("toasterToastType"));
    }

    @Test
    public void testChoice() {
        final var doc = generator.getApiDeclaration("choice-test", null, uriInfo);
        assertNotNull(doc);

        final var schemas = doc.components().schemas();
        final var firstContainer = schemas.get("choice-test_first-container");
        assertEquals("default-value", firstContainer.properties().get("leaf-default").defaultValue().toString());
        assertFalse(firstContainer.properties().containsKey("leaf-non-default"));

        final var secondContainer = schemas.get("choice-test_second-container");
        assertTrue(secondContainer.properties().containsKey("leaf-first-case"));
        assertFalse(secondContainer.properties().containsKey("leaf-second-case"));
    }

    @Test
    public void testMandatory() {
        final var doc = generator.getApiDeclaration(MANDATORY_TEST, null, uriInfo);
        assertNotNull(doc);
        final var schemas = doc.components().schemas();
        final var containersWithRequired = new ArrayList<String>();

        final var reqRootContainerElements = List.of("mandatory-root-leaf", "mandatory-container",
            "mandatory-first-choice", "mandatory-list");
        verifyRequiredField(schemas.get(CONFIG_ROOT_CONTAINER), reqRootContainerElements);
        containersWithRequired.add(CONFIG_ROOT_CONTAINER);

        final var reqMandatoryContainerElements = List.of("mandatory-leaf", "leaf-list-with-min-elements");
        verifyRequiredField(schemas.get(CONFIG_MANDATORY_CONTAINER), reqMandatoryContainerElements);
        containersWithRequired.add(CONFIG_MANDATORY_CONTAINER);

        final var reqMandatoryListElements = List.of("mandatory-list-field");
        verifyRequiredField(schemas.get(CONFIG_MANDATORY_LIST), reqMandatoryListElements);
        containersWithRequired.add(CONFIG_MANDATORY_LIST);

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

            final var patch = path.patch();
            assertNotNull(patch);
            assertEquals(expectedSize, patch.parameters().size());
        }

        // we do not generate POST for lists
        final var path = paths.get("/rests/data/recursive:container-root");
        final var post = path.post();
        final int expectedSize = configPaths.get("/rests/data/recursive:container-root");
        assertEquals(expectedSize, post.parameters().size());
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
        assertEquals(List.of("name"), getPathGetParameters(doc.paths(), pathToList1));

        var pathToList2 = "/rests/data/path-params-test:cont/list1={name}/list2={name1}";
        assertTrue(doc.paths().containsKey(pathToList2));
        assertEquals(List.of("name", "name1"), getPathGetParameters(doc.paths(), pathToList2));

        var pathToList3 = "/rests/data/path-params-test:cont/list3={name}";
        assertTrue(doc.paths().containsKey(pathToList3));
        assertEquals(List.of("name"), getPathGetParameters(doc.paths(), pathToList3));

        var pathToList4 = "/rests/data/path-params-test:cont/list1={name}/list4={name1}";
        assertTrue(doc.paths().containsKey(pathToList4));
        assertEquals(List.of("name", "name1"), getPathGetParameters(doc.paths(), pathToList4));

        var pathToList5 = "/rests/data/path-params-test:cont/list1={name}/cont2";
        assertTrue(doc.paths().containsKey(pathToList4));
        assertEquals(List.of("name"), getPathGetParameters(doc.paths(), pathToList5));
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
            assertEquals("integer", doc.paths().get(path).get().parameters().get(0).schema().type());
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
        assertEquals(List.of("name"), getPathPostParameters(doc.paths(), pathWithParameters));

        final var pathWithoutParameters = "/rests/operations/action-types:multi-container/inner-container/action";
        assertTrue(doc.paths().containsKey(pathWithoutParameters));
        assertEquals(List.of(), getPathPostParameters(doc.paths(), pathWithoutParameters));
    }

    @Test
    public void testSimpleOpenApiObjects() {
        final var doc = generator.getApiDeclaration("my-yang", "2022-10-06", uriInfo);

        assertEquals(Set.of("/rests/data", "/rests/data/my-yang:data"), doc.paths().keySet());
        final var JsonNodeMyYangData = doc.paths().get("/rests/data/my-yang:data");
        verifyRequestRef(JsonNodeMyYangData.put(), "#/components/schemas/my-yang_data", CONTAINER);
        verifyRequestRef(JsonNodeMyYangData.get(), "#/components/schemas/my-yang_data", CONTAINER);

        // Test `components/schemas` objects
        final var definitions = doc.components().schemas();
        assertEquals(1, definitions.size());
        assertTrue(definitions.containsKey("my-yang_data"));
    }

    @Test
    public void testToaster2OpenApiObjects() {
        final var doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

        final var jsonNodeToaster = doc.paths().get("/rests/data/toaster2:toaster");
        verifyRequestRef(jsonNodeToaster.post(), "#/components/schemas/toaster2_toaster_toasterSlot", LIST);
        verifyRequestRef(jsonNodeToaster.put(), "#/components/schemas/toaster2_toaster", CONTAINER);
        verifyRequestRef(jsonNodeToaster.get(), "#/components/schemas/toaster2_toaster", CONTAINER);

        final var jsonNodeToasterSlot = doc.paths().get("/rests/data/toaster2:toaster/toasterSlot={slotId}");
        verifyRequestRef(jsonNodeToasterSlot.put(), "#/components/schemas/toaster2_toaster_toasterSlot", LIST);
        verifyRequestRef(jsonNodeToasterSlot.get(), "#/components/schemas/toaster2_toaster_toasterSlot", LIST);

        final var jsonNodeSlotInfo = doc.paths().get(
            "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo");
        verifyRequestRef(jsonNodeSlotInfo.put(), "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo",
            CONTAINER);
        verifyRequestRef(jsonNodeSlotInfo.get(), "#/components/schemas/toaster2_toaster_toasterSlot_slotInfo",
            CONTAINER);

        final var jsonNodeLst = doc.paths().get("/rests/data/toaster2:lst={lf1}");
        verifyRequestRef(jsonNodeLst.put(), "#/components/schemas/toaster2_lst", LIST);
        verifyRequestRef(jsonNodeLst.get(), "#/components/schemas/toaster2_lst", LIST);

        final var jsonNodeLst1 = doc.paths().get("/rests/data/toaster2:lst={lf1}/lst1={key1},{key2}");
        verifyRequestRef(jsonNodeLst1.put(), "#/components/schemas/toaster2_lst_lst1", LIST);
        verifyRequestRef(jsonNodeLst1.get(), "#/components/schemas/toaster2_lst_lst1", LIST);

        final var jsonNodeMakeToast = doc.paths().get("/rests/operations/toaster2:make-toast");
        assertNull(jsonNodeMakeToast.get());
        verifyRequestRef(jsonNodeMakeToast.post(), "#/components/schemas/toaster2_make-toast_input", CONTAINER);

        final var jsonNodeCancelToast = doc.paths().get("/rests/operations/toaster2:cancel-toast");
        assertNull(jsonNodeCancelToast.get());
        // Test RPC with empty input
        final var postContent = jsonNodeCancelToast.post().requestBody().content();
        final var jsonSchema = postContent.get("application/json").schema();
        assertNull(jsonSchema.ref());
        final var xmlSchema = postContent.get("application/xml").schema();
        assertNull(xmlSchema.ref());

        // Test `components/schemas` objects
        final var definitions = doc.components().schemas();
        assertEquals(10, definitions.size());
    }

    /**
     * Test that checks if securitySchemes and security elements are present.
     */
    @Test
    public void testAuthenticationFeature() {
        final var doc = generator.getApiDeclaration(TOASTER_2, REVISION_DATE, uriInfo);

        assertEquals("[{basicAuth=[]}]", doc.security().toString());
        assertEquals("Http[type=http, scheme=basic, description=null, bearerFormat=null]",
            doc.components().securitySchemes().get(BASIC_AUTH_NAME).toString());

        // take list of all defined security scheme objects => all names of registered SecuritySchemeObjects
        final var securitySchemesObjectNames = doc.components().securitySchemes().keySet();
        assertTrue("No Security Schemes Object is defined", securitySchemesObjectNames.size() > 0);

        // collect all referenced security scheme objects
        final var referencedSecurityObjects = new HashSet<String>();
        doc.security().forEach(map -> referencedSecurityObjects.addAll(map.keySet()));

        // verify, that each reference references name of registered Security Scheme Object
        for (final var secObjRef : referencedSecurityObjects) {
            assertTrue(securitySchemesObjectNames.contains(secObjRef));
        }
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
        final var content = path.post().requestBody().content().get("application/xml");
        assertNotNull(content);
        final var schema = content.schema();
        assertNotNull(schema);
        final var xml = schema.xml();
        assertNotNull(xml);
        final var namespace = xml.namespace();
        assertNotNull(namespace);
        assertEquals("http://netconfcentral.org/ns/toaster", namespace);
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
        final var content = path.post().requestBody().content().get("application/xml");
        assertNotNull(content);
        final var schema = content.schema();
        assertNotNull(schema);
        final var xml = schema.xml();
        assertNotNull(xml);
        final var namespace = xml.namespace();
        assertNotNull(namespace);
        assertEquals("urn:ietf:params:xml:ns:yang:test:action:types", namespace);
    }

    /**
     * Test that checks if list min-elements and max-elements are present.
     * Also checks if number of example elements meets the min-elements condition
     * and if key defined leaf have unique values.
     */
    @Test
    public void testListExamplesWithNonKeyLeaf() {
        final var doc = generator.getApiDeclaration("test-container-childs", "2023-09-28", uriInfo);
        assertNotNull("Failed to find Datastore API", doc);
        final var components = doc.components();
        final var component = components.schemas().get("test-container-childs_root-container_nested-container");
        assertNotNull(component);
        assertNotNull(component.properties());
        final var property = component.properties().get("mandatory-list");
        assertNotNull(property);
        assertNotNull(property.minItems());
        assertNotNull(property.maxItems());
        assertEquals(3, (int) property.minItems());
        assertEquals(5, (int) property.maxItems());
        final var example = property.example();
        assertNotNull(example);
        assertEquals(3, ((List<?>)example).size());
        assertTrue(checkUniqueExample(example, "id"));
        assertFalse(checkUniqueExample(example, "name"));
        assertFalse(checkUniqueExample(example, "address"));
    }

    /**
     * Test that checks if multiple key leafs have unique values.
     * Also checks if nested container node is ignored.
     */
    @Test
    public void testListExamplesWithTwoKeys() {
        final var doc = generator.getApiDeclaration("test-container-childs", "2023-09-28", uriInfo);
        assertNotNull("Failed to find Datastore API", doc);
        final var components = doc.components();
        final var component = components.schemas()
            .get("test-container-childs_root-container-two-keys_nested-container-two-keys");
        assertNotNull(component);
        assertNotNull(component.properties());
        final var property = component.properties().get("mandatory-list-two-keys");
        assertNotNull(property);
        final var example = property.example();
        assertNotNull(example);
        assertTrue(checkUniqueExample(example, "id"));
        assertTrue(checkUniqueExample(example, "name"));
        assertFalse(checkUniqueExample(example, "address"));
        assertEquals(3, ((ArrayList<Map<?,?>>)example).get(0).size());
    }

    /**
     * Test that checks if sets of unique defined leafs have unique combination of values.
     */
    @Test
    public void testListExamplesWithUnique() {
        final var doc = generator.getApiDeclaration("test-container-childs", "2023-09-28", uriInfo);
        assertNotNull("Failed to find Datastore API", doc);
        final var components = doc.components();
        final var component = components.schemas()
            .get("test-container-childs_root-container-unique_nested-container-unique");
        assertNotNull(component);
        assertNotNull(component.properties());
        final var property = component.properties().get("mandatory-list-unique");
        assertNotNull(property);
        final var example = property.example();
        assertNotNull(example);
        assertTrue(checkUniqueExample(example, "id"));
        assertTrue(checkUniqueExample(example, "name") || checkUniqueExample(example, "address"));
        assertFalse(checkUniqueExample(example, "description"));
    }

    private static boolean checkUniqueExample(final Object examples, final String key) {
        assertEquals(ArrayList.class, examples.getClass());
        final var exampleValues = new ArrayList<>();

        for (Map<String, Object> example : (ArrayList<Map<String, Object>>)examples) {
            exampleValues.add(example.get(key));
        }
        return (exampleValues.size() == new HashSet<>(exampleValues).size());
    }

    /**
     * Test that number of elements in payload is correct.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testLeafListWithMinElementsPayload() {
        final var doc = generator.getApiDeclaration(MANDATORY_TEST, null, uriInfo);
        assertNotNull(doc);
        final var paths = doc.paths();
        final var path = paths.get("/rests/data/mandatory-test:root-container/mandatory-container");
        assertNotNull(path);
        final var requestBody = path.put().requestBody().content();
        assertNotNull(requestBody);
        final var jsonRef = requestBody.get("application/json").schema().properties()
            .get("mandatory-test:mandatory-container").ref();
        assertNotNull(jsonRef);
        final var xmlRef = requestBody.get("application/xml").schema().ref();
        assertNotNull(xmlRef);
        final var schema = doc.components().schemas().get("mandatory-test_root-container_mandatory-container");
        assertNotNull(schema);
        final var minItems = schema.properties().get("leaf-list-with-min-elements").minItems();
        assertNotNull(minItems);
        final var listOfExamples = ((List<String>) schema.properties().get("leaf-list-with-min-elements").example());
        assertNotNull(listOfExamples);
        assertEquals(jsonRef, xmlRef);
        assertEquals(listOfExamples.size(), minItems.intValue());
    }

    private static void verifyRequestRef(final Operation operation, final String expectedRef, final String nodeType) {
        final Map<String, MediaTypeObject> postContent;
        if (operation.requestBody() != null) {
            postContent = operation.requestBody().content();
        } else {
            postContent = operation.responses().get("200").content();
        }
        assertNotNull(postContent);
        final String postJsonRef;
        if (nodeType.equals(CONTAINER)) {
            postJsonRef = postContent.get("application/json").schema().properties().values().iterator().next().ref();
        } else {
            postJsonRef = postContent.get("application/json").schema().properties().values().iterator().next().items()
                .ref();
        }
        assertNotNull(postJsonRef);
        assertEquals(expectedRef, postJsonRef);
        final var postXmlRef = postContent.get("application/xml").schema().ref();
        assertNotNull(postXmlRef);
        assertEquals(expectedRef, postXmlRef);
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

    private static void verifyRequiredField(final Schema rootContainer, final List<String> expected) {
        assertNotNull(rootContainer);
        final var required = rootContainer.required();
        assertNotNull(required);
        assertEquals(expected, required);
    }

    private static Set<String> extractSchemaRefFromPath(final Path path) {
        if (path == null) {
            return Set.of();
        }
        final var references = new HashSet<String>();
        final var get = path.get();
        if (get != null) {
            references.addAll(schemaRefFromContent(get.responses().get("200").content()));
        }
        final var post = path.post();
        if (post != null) {
            references.addAll(schemaRefFromContent(post.requestBody().content()));
        }
        final var put = path.put();
        if (put != null) {
            references.addAll(schemaRefFromContent(put.requestBody().content()));
        }
        final var patch = path.patch();
        if (patch != null) {
            references.addAll(schemaRefFromContent(patch.requestBody().content()));
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
    private static Set<String> schemaRefFromContent(final Map<String, MediaTypeObject> content) {
        final HashSet<String> refs = new HashSet<>();
        content.values().forEach(mediaType -> {
            final var schema = mediaType.schema();
            final var props = mediaType.schema().properties();
            final String ref;
            if (props == null) {
                // either there is no node with the key "properties", try to find immediate child of schema
                ref = schema.ref();
            } else if (props.values().iterator().next().items() == null) {
                // or the "properties" is defined and under that we didn't find the "items" node
                // try to get "$ref" as immediate child under properties
                ref = props.values().iterator().next().ref();
            } else {
                // or the "items" node is defined, in which case we try to get the "$ref" from this node
                ref = props.values().iterator().next().items().ref();
            }

            if (ref != null) {
                refs.add(ref.replaceFirst(COMPONENTS_PREFIX, ""));
            }
        });
        return refs;
    }
}
