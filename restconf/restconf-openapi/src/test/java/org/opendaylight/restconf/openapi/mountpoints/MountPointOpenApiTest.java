/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.mountpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.openapi.OpenApiTestUtils.getPathGetParameters;
import static org.opendaylight.restconf.openapi.OpenApiTestUtils.getPathPostParameters;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.BASIC_AUTH_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.internal.util.collection.ImmutableMultivaluedMap;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public final class MountPointOpenApiTest {
    private static final String TOASTER = "toaster";
    private static final String TOASTER_REVISION = "2009-11-20";
    private static final Long DEVICE_ID = 1L;
    private static final String DEVICE_NAME = "123";
    private static final String TOASTER_NODE_PATH = "/rests/data/nodes/node=123/yang-ext:mount/toaster:toaster";
    private static final String TOASTER_NODE_GET_SUMMARY = "GET - " + DEVICE_NAME + " - toaster - toaster";
    private static final String GET_ALL = "http://localhost:8181/openapi/api/v3/mounts/" + DEVICE_ID;
    private static final String GET_TOASTER = "http://localhost:8181/openapi/api/v3/mounts/%s/%s(%s)".formatted(
        DEVICE_ID, TOASTER, TOASTER_REVISION);
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), DEVICE_NAME).build();
    private static final String INSTANCE_URL = "/nodes/node=123/";

    private static EffectiveModelContext context;
    private static DOMSchemaService schemaService;

    private MountPointOpenApi openApi;
    private UriInfo uriDeviceAll;
    private UriInfo uriDeviceToaster;

    @BeforeClass
    public static void beforeClass() {
        schemaService = mock(DOMSchemaService.class);
        context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        when(schemaService.getGlobalContext()).thenReturn(context);
    }

    @Before
    public void before() throws Exception {
        // We are sharing the global schema service and the mount schema service
        // in our test.
        // OK for testing - real thing would have separate instances.
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        openApi = new MountPointOpenApiGeneratorRFC8040(schemaService, service).getMountPointOpenApi();

        uriDeviceAll = DocGenTestHelper.createMockUriInfo(GET_ALL);
        when(uriDeviceAll.getQueryParameters()).thenReturn(ImmutableMultivaluedMap.empty());
        uriDeviceToaster = DocGenTestHelper.createMockUriInfo(GET_TOASTER);
        when(uriDeviceToaster.getQueryParameters()).thenReturn(ImmutableMultivaluedMap.empty());
    }

    @Test()
    public void getInstanceIdentifiers() {
        assertEquals(0, openApi.getInstanceIdentifiers().size());
        openApi.onMountPointCreated(INSTANCE_ID); // add this ID into the list of mount points
        assertEquals(1, openApi.getInstanceIdentifiers().size());
        assertEquals((Long) 1L, openApi.getInstanceIdentifiers().entrySet().iterator().next()
                .getValue());
        assertEquals(INSTANCE_URL, openApi.getInstanceIdentifiers().entrySet().iterator().next()
                .getKey());
        openApi.onMountPointRemoved(INSTANCE_ID); // remove ID from list of mount points
        assertEquals(0, openApi.getInstanceIdentifiers().size());
    }

    @Test
    public void testGetDataStoreApi() throws Exception {
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID); // add this ID into the list of mount points

        final OpenApiObject mountPointApi = openApi.getMountPointApi(mockInfo, 1L, "Datastores", "-");
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final Map<String, Path> paths = mountPointApi.paths();
        assertNotNull(paths);

        assertEquals("Unexpected api list size", 1, paths.size());

        final Set<String> actualUrls = new TreeSet<>();

        for (final Map.Entry<String, Path> path : paths.entrySet()) {
            actualUrls.add(path.getKey());
            final Operation getOperation = path.getValue().get();
            assertNotNull("Unexpected operation method on " + path, getOperation);
            assertNotNull("Expected non-null desc on " + path, getOperation.description());
        }

        assertEquals(Set.of("/rests/operations" + INSTANCE_URL + "yang-ext:mount"), actualUrls);
    }

    /**
     * Test that creates mount point api with all models from yang folder and checks operation paths for these models.
     */
    @Test
    public void testGetMountPointApi() throws Exception {
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);

        final OpenApiObject mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final Map<String, Path> paths = mountPointApi.paths();
        assertNotNull(paths);

        assertEquals("Unexpected api list size", 78, paths.size());

        final List<Operation> getOperations = new ArrayList<>();
        final List<Operation> postOperations = new ArrayList<>();
        final List<Operation> putOperations = new ArrayList<>();
        final List<Operation> patchOperations = new ArrayList<>();
        final List<Operation> deleteOperations = new ArrayList<>();

        for (final Map.Entry<String, Path> path : paths.entrySet()) {
            Optional.ofNullable(path.getValue().get()).ifPresent(getOperations::add);
            Optional.ofNullable(path.getValue().post()).ifPresent(postOperations::add);
            Optional.ofNullable(path.getValue().put()).ifPresent(putOperations::add);
            Optional.ofNullable(path.getValue().patch()).ifPresent(patchOperations::add);
            Optional.ofNullable(path.getValue().delete()).ifPresent(deleteOperations::add);
        }

        assertEquals("Unexpected GET paths size", 70, getOperations.size());
        assertEquals("Unexpected POST paths size", 22, postOperations.size());
        assertEquals("Unexpected PUT paths size", 68, putOperations.size());
        assertEquals("Unexpected PATCH paths size", 68, patchOperations.size());
        assertEquals("Unexpected DELETE paths size", 68, deleteOperations.size());
    }

    /**
     * Test that checks for correct amount of parameters in requests.
     */
    @Test
    @SuppressWarnings("checkstyle:lineLength")
    public void testMountPointRecursiveParameters() throws Exception {
        final var configPaths = Map.of("/rests/data/nodes/node=123/yang-ext:mount/recursive:container-root", 0,
            "/rests/data/nodes/node=123/yang-ext:mount/recursive:container-root/root-list={name}", 1,
            "/rests/data/nodes/node=123/yang-ext:mount/recursive:container-root/root-list={name}/nested-list={name1}", 2,
            "/rests/data/nodes/node=123/yang-ext:mount/recursive:container-root/root-list={name}/nested-list={name1}/super-nested-list={name2}", 3);

        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);

        final var mountPointApi = openApi.getMountPointApi(mockInfo, 1L, "recursive", "2023-05-22");
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final var paths = mountPointApi.paths();
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

        // POST request exists only for containers
        final var post = paths.get("/rests/data/nodes/node=123/yang-ext:mount/recursive:container-root").post();
        assertNotNull(post);
        final int expectedSize = configPaths.get("/rests/data/nodes/node=123/yang-ext:mount/recursive:container-root");
        assertEquals(expectedSize, post.parameters().size());
    }

    /**
     * Test that request parameters are correctly numbered.
     *
     * <p>
     * It means we should have name and name1, etc. when we have the same parameter in path multiple times.
     */
    @Test
    public void testParametersNumberingForMountPointApi() throws Exception {
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);

        final OpenApiObject mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find Datastore API", mountPointApi);

        var pathToList1 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}";
        assertTrue(mountPointApi.paths().containsKey(pathToList1));
        assertEquals(List.of("name"), getPathGetParameters(mountPointApi.paths(), pathToList1));

        var pathToList2 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/list2={name1}";
        assertTrue(mountPointApi.paths().containsKey(pathToList2));
        assertEquals(List.of("name", "name1"), getPathGetParameters(mountPointApi.paths(), pathToList2));

        var pathToList3 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list3={name}";
        assertTrue(mountPointApi.paths().containsKey(pathToList3));
        assertEquals(List.of("name"), getPathGetParameters(mountPointApi.paths(), pathToList3));

        var pathToList4 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/list4={name1}";
        assertTrue(mountPointApi.paths().containsKey(pathToList4));
        assertEquals(List.of("name", "name1"), getPathGetParameters(mountPointApi.paths(), pathToList4));

        var pathToList5 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/cont2";
        assertTrue(mountPointApi.paths().containsKey(pathToList5));
        assertEquals(List.of("name"), getPathGetParameters(mountPointApi.paths(), pathToList5));
    }

    /**
     * Test that request parameters are correctly typed.
     */
    @Test
    public void testParametersTypesForMountPointApi() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);
        final var doc = openApi.getMountPointApi(mockInfo, 1L, null);
        final var pathToContainer = "/rests/data/nodes/node=123/yang-ext:mount/typed-params:typed/";
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
    public void testActionPathsParamsForMountPointApi() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);

        final var mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final var pathWithParameters =
            "/rests/operations/nodes/node=123/yang-ext:mount/action-types:list={name}/list-action";
        assertTrue(mountPointApi.paths().containsKey(pathWithParameters));
        assertEquals(List.of("name"), getPathPostParameters(mountPointApi.paths(), pathWithParameters));

        final var pathWithoutParameters =
            "/rests/operations/nodes/node=123/yang-ext:mount/action-types:multi-container/inner-container/action";
        assertTrue(mountPointApi.paths().containsKey(pathWithoutParameters));
        assertEquals(List.of(), getPathPostParameters(mountPointApi.paths(), pathWithoutParameters));
    }

    /**
     * Test that checks if securitySchemes and security elements are present.
     */
    @Test
    public void testAuthenticationFeature() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);
        final var mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);

        assertEquals("[{basicAuth=[]}]", mountPointApi.security().toString());
        assertEquals("Http[type=http, scheme=basic, description=null, bearerFormat=null]",
            mountPointApi.components().securitySchemes().get(BASIC_AUTH_NAME).toString());
    }

    @Test
    public void testSummaryForAllModules() {
        openApi.onMountPointCreated(INSTANCE_ID);
        // get OpenApiObject for the device (all modules)
        final OpenApiObject openApiAll = openApi.getMountPointApi(uriDeviceAll, DEVICE_ID, null);
        final String getToasterSummary = openApiAll.paths()
            .get(TOASTER_NODE_PATH)
            .get()
            .summary();
        assertEquals(TOASTER_NODE_GET_SUMMARY, getToasterSummary);
    }

    @Test
    public void testSummaryForSingleModule() {
        openApi.onMountPointCreated(INSTANCE_ID);
        // get OpenApiObject for a specific module (toaster) associated with the mounted device
        final OpenApiObject openApiToaster = openApi.getMountPointApi(uriDeviceToaster, DEVICE_ID, TOASTER,
            TOASTER_REVISION);
        final String getToasterSummary = openApiToaster.paths()
            .get(TOASTER_NODE_PATH)
            .get()
            .summary();
        assertEquals(TOASTER_NODE_GET_SUMMARY, getToasterSummary);
    }

    @Test
    public void testPathsForSpecificModuleOfMounted() {
        openApi.onMountPointCreated(INSTANCE_ID);

        // get OpenApiObject for the device (all modules)
        final OpenApiObject openApiAll = openApi.getMountPointApi(uriDeviceAll, DEVICE_ID, null);
        // get OpenApiObject for a specific module (toaster(2009-11-20))
        final OpenApiObject openApiToaster = openApi.getMountPointApi(uriDeviceToaster, DEVICE_ID, TOASTER,
            TOASTER_REVISION);

        /*
            filter paths from openapi for all modules down to only those that are present in openapi for toaster.
            The Path object for the path, that in this case ends with "yang-ext:mount" is constructed in a different way
            when requesting OpenApiObject for a single module compared to requesting it for all modules.
            We do not want to include it in this particular comparison, so filter it out
         */
        final Set<Map.Entry<String, Path>> toasterPathsFromAll = openApiAll.paths().entrySet()
            .stream()
            .filter(e -> openApiToaster.paths().containsKey(e.getKey()) && !e.getKey().endsWith("yang-ext:mount"))
            .collect(Collectors.toSet());

        final Set<Map.Entry<String, Path>> toasterPathsFromToaster = openApiToaster.paths().entrySet()
            .stream()
            .filter(e -> !e.getKey().endsWith("yang-ext:mount"))
            .collect(Collectors.toSet());

        // verify that the filtered set (from openapi for all modules) is the same as the set from openapi for toaster
        assertEquals(toasterPathsFromToaster, toasterPathsFromAll);
    }

    /**
     * Test that checks if namespace for rpc is present.
     */
    @Test
    public void testRpcNamespace() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);

        final var mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find Datastore API", mountPointApi);
        final var paths = mountPointApi.paths();
        final var path = paths.get("/rests/operations/nodes/node=123/yang-ext:mount/toaster:cancel-toast");
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
    public void testActionsNamespace() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);
        final var mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find Datastore API", mountPointApi);
        final var paths = mountPointApi.paths();
        final var path = paths.get(
            "/rests/operations/nodes/node=123/yang-ext:mount/action-types:multi-container/inner-container/action");
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
     * Test that number of elements in payload is correct.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testLeafListWithMinElementsPayloadOnMountPoint() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);
        final var mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull(mountPointApi);
        final var paths = mountPointApi.paths();
        final var path =
            paths.get("/rests/data/nodes/node=123/yang-ext:mount/mandatory-test:root-container/mandatory-container");
        assertNotNull(path);
        final var requestBody = path.put().requestBody().content();
        assertNotNull(requestBody);
        final var jsonRef = requestBody.get("application/json").schema().properties()
            .get("mandatory-test:mandatory-container").ref();
        assertNotNull(jsonRef);
        final var xmlRef = requestBody.get("application/xml").schema().ref();
        assertNotNull(xmlRef);
        final var schema =
            mountPointApi.components().schemas().get("mandatory-test_root-container_mandatory-container");
        assertNotNull(schema);
        final var minItems = schema.properties().get("leaf-list-with-min-elements").minItems();
        assertNotNull(minItems);
        final var listOfExamples = ((List<String>) schema.properties().get("leaf-list-with-min-elements").example());
        assertNotNull(listOfExamples);
        assertEquals(jsonRef, xmlRef);
        assertEquals(listOfExamples.size(), minItems.intValue());
    }
}
