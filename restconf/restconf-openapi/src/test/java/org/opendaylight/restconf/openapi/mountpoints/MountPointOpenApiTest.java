/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.mountpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.openapi.OpenApiTestUtils.getPathParameters;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public final class MountPointOpenApiTest {
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();
    private static final String INSTANCE_URL = "/nodes/node=123/";

    private static EffectiveModelContext context;
    private static DOMSchemaService schemaService;

    private MountPointOpenApi openApi;

    @BeforeClass
    public static void beforeClass() {
        schemaService = mock(DOMSchemaService.class);
        context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        when(schemaService.getGlobalContext()).thenReturn(context);
    }

    @Before
    public void before() {
        // We are sharing the global schema service and the mount schema service
        // in our test.
        // OK for testing - real thing would have separate instances.
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        openApi = new MountPointOpenApiGeneratorRFC8040(schemaService, service).getMountPointOpenApi();
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

        final Map<String, Path> paths = mountPointApi.getPaths();
        assertNotNull(paths);

        assertEquals("Unexpected api list size", 2, paths.size());

        final Set<String> actualUrls = new TreeSet<>();

        for (final Map.Entry<String, Path> path : paths.entrySet()) {
            actualUrls.add(path.getKey());
            final JsonNode getOperation = path.getValue().getGet();
            assertNotNull("Unexpected operation method on " + path, getOperation);
            assertNotNull("Expected non-null desc on " + path, getOperation.get("description"));
        }

        assertEquals(Set.of("/rests/data" + INSTANCE_URL + "yang-ext:mount",
            "/rests/operations" + INSTANCE_URL + "yang-ext:mount"), actualUrls);
    }

    /**
     * Test that creates mount point api with all models from yang folder and checks operation paths for these models.
     */
    @Test
    public void testGetMountPointApi() throws Exception {
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);

        final OpenApiObject mountPointApi = openApi.getMountPointApi(mockInfo, 1L, Optional.empty());
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final Map<String, Path> paths = mountPointApi.getPaths();
        assertNotNull(paths);

        assertEquals("Unexpected api list size", 38, paths.size());

        final List<JsonNode> getOperations = new ArrayList<>();
        final List<JsonNode> postOperations = new ArrayList<>();
        final List<JsonNode> putOperations = new ArrayList<>();
        final List<JsonNode> patchOperations = new ArrayList<>();
        final List<JsonNode> deleteOperations = new ArrayList<>();

        for (final Map.Entry<String, Path> path : paths.entrySet()) {
            Optional.ofNullable(path.getValue().getGet()).ifPresent(getOperations::add);
            Optional.ofNullable(path.getValue().getPost()).ifPresent(postOperations::add);
            Optional.ofNullable(path.getValue().getPut()).ifPresent(putOperations::add);
            Optional.ofNullable(path.getValue().getPatch()).ifPresent(patchOperations::add);
            Optional.ofNullable(path.getValue().getDelete()).ifPresent(deleteOperations::add);
        }

        assertEquals("Unexpected GET paths size", 30, getOperations.size());
        assertEquals("Unexpected POST paths size", 36, postOperations.size());
        assertEquals("Unexpected PUT paths size", 28, putOperations.size());
        assertEquals("Unexpected PATCH paths size", 28, patchOperations.size());
        assertEquals("Unexpected DELETE paths size", 28, deleteOperations.size());
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

        final var paths = mountPointApi.getPaths();
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
    public void testParametersNumberingForMountPointApi() throws Exception {
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);

        final OpenApiObject mountPointApi = openApi.getMountPointApi(mockInfo, 1L, Optional.empty());
        assertNotNull("Failed to find Datastore API", mountPointApi);

        var pathToList1 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}";
        assertTrue(mountPointApi.getPaths().containsKey(pathToList1));
        assertEquals(List.of("name"), getPathParameters(mountPointApi.getPaths(), pathToList1));

        var pathToList2 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/list2={name1}";
        assertTrue(mountPointApi.getPaths().containsKey(pathToList2));
        assertEquals(List.of("name", "name1"), getPathParameters(mountPointApi.getPaths(), pathToList2));

        var pathToList3 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list3={name}";
        assertTrue(mountPointApi.getPaths().containsKey(pathToList3));
        assertEquals(List.of("name"), getPathParameters(mountPointApi.getPaths(), pathToList3));

        var pathToList4 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/list4={name1}";
        assertTrue(mountPointApi.getPaths().containsKey(pathToList4));
        assertEquals(List.of("name", "name1"), getPathParameters(mountPointApi.getPaths(), pathToList4));

        var pathToList5 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/cont2";
        assertTrue(mountPointApi.getPaths().containsKey(pathToList5));
        assertEquals(List.of("name"), getPathParameters(mountPointApi.getPaths(), pathToList5));
    }
}
