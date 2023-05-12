/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.mountpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.AbstractApiDocTest;
import org.opendaylight.netconf.sal.rest.doc.DocGenTestHelper;
import org.opendaylight.netconf.sal.rest.doc.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.netconf.sal.rest.doc.openapi.OpenApiObject;
import org.opendaylight.netconf.sal.rest.doc.openapi.Path;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@RunWith(Parameterized.class)
public final class MountPointOpenApiTest extends AbstractApiDocTest {

    private static final String DEFAULT_BASE_PATH = "rests";
    private static final String CUSTOM_BASE_PATH = "restconf";

    @Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {
                createMountPointGenerator((service) ->
                        new MountPointOpenApiGeneratorRFC8040(SCHEMA_SERVICE, service)),
                DEFAULT_BASE_PATH
            },
            {
                createMountPointGenerator((service) ->
                        new MountPointOpenApiGeneratorRFC8040(SCHEMA_SERVICE, service, CUSTOM_BASE_PATH)),
                CUSTOM_BASE_PATH
            }
        });
    }

    private static Function<DOMMountPointService, MountPointOpenApiGeneratorRFC8040> createMountPointGenerator(
            final Function<DOMMountPointService, MountPointOpenApiGeneratorRFC8040> generatorFactory) {
        return generatorFactory;
    }

    @Parameter(0)
    public Function<DOMMountPointService, MountPointOpenApiGeneratorRFC8040> mountPointConstructor;
    @Parameter(1)
    public String basePath;

    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();
    private static final String INSTANCE_URL = "/nodes/node=123/";

    private MountPointOpenApi openApi;

    @Before
    public void before() {
        // We are sharing the global schema service and the mount schema service
        // in our test.
        // OK for testing - real thing would have separate instances.
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(SCHEMA_SERVICE));

        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        openApi = mountPointConstructor.apply(service).getMountPointOpenApi();
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

        assertEquals(Set.of("/" + basePath + "/data" + INSTANCE_URL + "yang-ext:mount",
            "/" + basePath + "/operations" + INSTANCE_URL + "yang-ext:mount"), actualUrls);
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

        assertEquals("Unexpected api list size", 36, paths.size());

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

        assertEquals("Unexpected GET paths size", 24, getOperations.size());
        assertEquals("Unexpected POST paths size", 34, postOperations.size());
        assertEquals("Unexpected PUT paths size", 22, putOperations.size());
        assertEquals("Unexpected PATCH paths size", 22, patchOperations.size());
        assertEquals("Unexpected DELETE paths size", 22, deleteOperations.size());
    }

    @Test
    public void testOperationPathsWithDefaultBasePath() throws Exception {
        // default base path -> basePath = "rests"
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        openApi.onMountPointCreated(INSTANCE_ID);

        final OpenApiObject mountPointApi = openApi.getMountPointApi(mockInfo, 1L, Optional.empty());
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final Map<String, Path> paths = mountPointApi.getPaths();
        assertNotNull(paths);

        assertEquals("Unexpected api list size", 36, paths.size());

        Set<String> actionPaths = Set.of(
            "/" + basePath + "/operations/nodes/node=123/yang-ext:mount/action-types:container/container-action",
            "/" + basePath
                    + "/operations/nodes/node=123/yang-ext:mount/action-types:multi-container/inner-container/action",
            "/" + basePath + "/operations/nodes/node=123/yang-ext:mount/action-types:list={name}/list-action",
            "/" + basePath + "/operations/nodes/node=123/yang-ext:mount/action-path-test:top/top-action",
            "/" + basePath + "/operations/nodes/node=123/yang-ext:mount/action-path-test:top/mid/mid-action",
            "/" + basePath + "/operations/nodes/node=123/yang-ext:mount/action-path-test:top/mid/bottom/bottom-action"
        );

        // FIXME: NETCONF-1021 remove this mapping after fixing hardcoded path: hardcoded solution doesn't start with /
        List<String> modifiedPaths = actionPaths.stream().map(s -> s.substring(1)).collect(Collectors.toList());

        // FIXME: NETCONF-1021 remove the assumption after fixing hardcoded path problem
        assumeTrue(basePath.equals(DEFAULT_BASE_PATH));

        modifiedPaths.forEach(path -> assertTrue(paths.containsKey(path)));
    }
}
