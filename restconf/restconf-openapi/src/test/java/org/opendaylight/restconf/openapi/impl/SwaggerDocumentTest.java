/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.glassfish.jersey.internal.util.collection.ImmutableMultivaluedMap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class SwaggerDocumentTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private static DOMSchemaService schemaService;
    private static OpenApiService openApiService;

    @BeforeClass
    public static void beforeClass() {
        schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResource("/swagger-document/toaster.yang");
        when(schemaService.getGlobalContext()).thenReturn(context);

        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final var service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService, service);
        final var openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService);
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(INSTANCE_ID);
        openApiService = new OpenApiServiceImpl(mountPointRFC8040, openApiGeneratorRFC8040);
    }

    /**
     * Tests the swagger document that is result of the call to the '/single' endpoint.
     */
    @Test
    public void getAllModulesDocTest() throws Exception {
        final var getAllController = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/single");
        final var controllerDocAll = openApiService.getAllModulesDoc(getAllController);

        var jsonControllerDoc = MAPPER.readTree(MAPPER.writeValueAsString(controllerDocAll.getEntity()));
        final var expectedJson = MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("swagger-document/controller-all.json"));
        assertEquals(expectedJson, jsonControllerDoc);

//        var jsonControllerDocother = MAPPER.valueToTree(controllerDocAll.getEntity());
//        // but this does not even though it is processing the same object as the previous one
//        assertEquals(expectedJson, jsonControllerDocother);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster(2009-11-20)' endpoint.
     */
    @Test
    public void getDocByModuleTest() throws Exception {
        final var getToasterController = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/toaster(2009-11-20)");
        final var controllerDocToaster = openApiService.getDocByModule("toaster", "2009-11-20", getToasterController);
        final var jsonControllerDoc = MAPPER.valueToTree(controllerDocToaster.getEntity());
        final var expectedJson = MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("swagger-document/controller-toaster.json"));
        assertEquals(expectedJson, jsonControllerDoc);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1' endpoint.
     */
    @Test
    public void getMountDocTest() throws Exception {
        final var getAllDevice = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/mounts/1");
        when(getAllDevice.getQueryParameters()).thenReturn(ImmutableMultivaluedMap.empty());
        final var deviceDocAll = openApiService.getMountDoc("1", getAllDevice);
        final var jsonDeviceDoc = MAPPER.valueToTree(deviceDocAll.getEntity());
        final var expectedJson = MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("swagger-document/device-all.json"));
        assertEquals(expectedJson, jsonDeviceDoc);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster(2009-11-20)' endpoint.
     */
    @Test
    public void getMountDocByModuleTest() throws Exception {
        final var getToasterDevice = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/mounts/1/toaster(2009-11-20)");
        final var deviceDocToaster = openApiService.getMountDocByModule("1", "toaster", "2009-11-20", getToasterDevice);
        final var jsonDeviceDoc = MAPPER.valueToTree(deviceDocToaster.getEntity());
        final var expectedJson = MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("swagger-document/device-toaster.json"));
        assertEquals(expectedJson, jsonDeviceDoc);
    }
}
