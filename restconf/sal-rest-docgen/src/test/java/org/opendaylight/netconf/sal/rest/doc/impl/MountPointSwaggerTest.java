/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.mountpoints.MountPointSwagger;
import org.opendaylight.netconf.sal.rest.doc.swagger.OpenApiObject;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public final class MountPointSwaggerTest extends AbstractApiDocTest {
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();
    private static final String INSTANCE_URL = "/nodes/node=123/";

    private MountPointSwagger swagger;

    @Before
    public void before() {
        // We are sharing the global schema service and the mount schema service
        // in our test.
        // OK for testing - real thing would have separate instances.
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(SCHEMA_SERVICE));

        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        swagger = new MountPointSwaggerGeneratorRFC8040(SCHEMA_SERVICE, service).getMountPointSwagger();
    }

    @Test()
    public void getInstanceIdentifiers() {
        assertEquals(0, swagger.getInstanceIdentifiers().size());
        swagger.onMountPointCreated(INSTANCE_ID); // add this ID into the list of mount points
        assertEquals(1, swagger.getInstanceIdentifiers().size());
        assertEquals((Long) 1L, swagger.getInstanceIdentifiers().entrySet().iterator().next()
                .getValue());
        assertEquals(INSTANCE_URL, swagger.getInstanceIdentifiers().entrySet().iterator().next()
                .getKey());
        swagger.onMountPointRemoved(INSTANCE_ID); // remove ID from list of mount points
        assertEquals(0, swagger.getInstanceIdentifiers().size());
    }

    @Test
    public void testGetDataStoreApi() throws Exception {
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        swagger.onMountPointCreated(INSTANCE_ID); // add this ID into the list of mount points

        final SwaggerObject mountPointApi = (SwaggerObject) swagger.getMountPointApi(mockInfo, 1L, "Datastores", "-",
            OAversion.V2_0);
        assertNotNull("failed to find Datastore API", mountPointApi);

        final ObjectNode pathsObject = mountPointApi.getPaths();
        assertNotNull(pathsObject);

        assertEquals("Unexpected api list size", 2, pathsObject.size());

        final Set<String> actualUrls = new TreeSet<>();

        final Iterator<Map.Entry<String, JsonNode>> fields = pathsObject.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final String path = field.getKey();
            final JsonNode operations = field.getValue();
            actualUrls.add(field.getKey());
            assertEquals("unexpected operations size on " + path, 1, operations.size());

            final JsonNode getOperation = operations.get("get");

            assertNotNull("unexpected operation method on " + path, getOperation);

            assertNotNull("expected non-null desc on " + path, getOperation.get("description"));
        }

        assertEquals(Set.of("/rests/data" + INSTANCE_URL + "yang-ext:mount",
            "/rests/operations" + INSTANCE_URL + "yang-ext:mount"), actualUrls);
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
        swagger.onMountPointCreated(INSTANCE_ID);

        final OpenApiObject mountPointApi = (OpenApiObject) swagger.getMountPointApi(mockInfo, 1L, Optional.empty(),
                OAversion.V3_0);
        assertNotNull("Failed to find Datastore API", mountPointApi);

        var pathToList1 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}";
        assertTrue(mountPointApi.getPaths().has(pathToList1));
        assertEquals(List.of("name"), getPathParameters(mountPointApi.getPaths(), pathToList1));

        var pathToList2 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/list2={name1}";
        assertTrue(mountPointApi.getPaths().has(pathToList2));
        assertEquals(List.of("name", "name1"), getPathParameters(mountPointApi.getPaths(), pathToList2));

        var pathToList3 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list3={name}";
        assertTrue(mountPointApi.getPaths().has(pathToList3));
        assertEquals(List.of("name"), getPathParameters(mountPointApi.getPaths(), pathToList3));

        var pathToList4 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/list4={name1}";
        assertTrue(mountPointApi.getPaths().has(pathToList4));
        assertEquals(List.of("name", "name1"), getPathParameters(mountPointApi.getPaths(), pathToList4));

        var pathToList5 = "/rests/data/nodes/node=123/yang-ext:mount/path-params-test:cont/list1={name}/cont2";
        assertTrue(mountPointApi.getPaths().has(pathToList5));
        assertEquals(List.of("name"), getPathParameters(mountPointApi.getPaths(), pathToList5));
    }

    /**
     * Test that request for actions is correct and has parameters.
     */
    @Test
    public void testActionPathsParamsForMountPointApi() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        swagger.onMountPointCreated(INSTANCE_ID);

        final var mountPointApi = (OpenApiObject) swagger.getMountPointApi(mockInfo, 1L, Optional.empty(),
            OAversion.V3_0);
        assertNotNull("Failed to find Datastore API", mountPointApi);

        final var pathWithParameters =
            "/rests/operations/nodes/node=123/yang-ext:mount/action-types:list={name}/list-action";
        assertTrue(mountPointApi.getPaths().has(pathWithParameters));
        assertEquals(List.of("name"), getPathParameters(mountPointApi.getPaths(), pathWithParameters));

        final var pathWithoutParameters =
            "/rests/operations/nodes/node=123/yang-ext:mount/action-types:multi-container/inner-container/action";
        assertTrue(mountPointApi.getPaths().has(pathWithoutParameters));
        assertEquals(List.of(), getPathParameters(mountPointApi.getPaths(), pathWithoutParameters));
    }

    /**
     * Test that checks if securitySchemes and security elements are present in OpenApi document.
     */
    @Test
    public void testAuthenticationFeatureV3() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        swagger.onMountPointCreated(INSTANCE_ID);
        final var mountPointApi = (OpenApiObject) swagger.getMountPointApi(mockInfo, 1L, Optional.empty(),
            OAversion.V3_0);

        assertEquals("[{\"basicAuth\":[]}]", mountPointApi.getSecurity().toString());
        assertEquals("{\"type\":\"http\",\"scheme\":\"basic\"}", mountPointApi.getComponents().getSecuritySchemes()
            .getBasicAuth().toString());
    }

    /**
     * Test that checks if securityDefinitions and security elements are present in Swagger document.
     */
    @Test
    public void testAuthenticationFeatureV2() throws Exception {
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        swagger.onMountPointCreated(INSTANCE_ID);
        final var mountPointApi = (SwaggerObject) swagger.getMountPointApi(mockInfo, 1L, Optional.empty(),
            OAversion.V2_0);

        assertEquals("[{\"basicAuth\":[]}]", mountPointApi.getSecurity().toString());
        assertEquals("{\"type\":\"basic\"}", mountPointApi.getSecurityDefinitions().getBasicAuth().toString());
    }
}
