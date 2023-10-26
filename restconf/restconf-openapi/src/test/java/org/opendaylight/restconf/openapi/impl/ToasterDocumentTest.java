/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

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
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class ToasterDocumentTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * We want flexibility in comparing the resulting JSONs by not enforcing strict ordering of array contents.
     * This comparison mode allows us to do that and also to restrict extensibility (extensibility = additional fields)
     */
    private static final JSONCompareMode IGNORE_ORDER = JSONCompareMode.NON_EXTENSIBLE;
    private static final String TOASTER = "toaster";
    private static final String TOASTER_REV = "2009-11-20";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private static OpenApiService openApiService;

    @BeforeClass
    public static void beforeClass() {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResource("/toaster-document/toaster.yang");
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
        final var controllerDocAll = openApiService.getAllModulesDoc(getAllController).getEntity();

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocAll);
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("toaster-document/controller-all.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster(2009-11-20)' endpoint.
     *
     * <p>
     * Model toaster is used for test correct generating of complex openapi object.
     */
    @Test
    public void getDocByModuleTest() throws Exception {
        final var getToasterController = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/toaster(2009-11-20)");
        final var controllerDocToaster = openApiService.getDocByModule(TOASTER, TOASTER_REV, getToasterController);

        final var jsonControllerDoc = MAPPER.writeValueAsString(controllerDocToaster.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("toaster-document/controller-toaster.json")));
        JSONAssert.assertEquals(expectedJson, jsonControllerDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1' endpoint.
     */
    @Test
    public void getMountDocTest() throws Exception {
        final var getAllDevice = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/mounts/1");
        when(getAllDevice.getQueryParameters()).thenReturn(ImmutableMultivaluedMap.empty());
        final var deviceDocAll = openApiService.getMountDoc("1", getAllDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocAll.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("toaster-document/device-all.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster(2009-11-20)' endpoint.
     *
     * <p>
     * Model toaster is used for test correct generating of complex openapi object.
     */
    @Test
    public void getMountDocByModuleTest() throws Exception {
        final var getToasterDevice = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/mounts/1/toaster(2009-11-20)");
        final var deviceDocToaster = openApiService.getMountDocByModule("1", TOASTER, TOASTER_REV, getToasterDevice);

        final var jsonDeviceDoc = MAPPER.writeValueAsString(deviceDocToaster.getEntity());
        final var expectedJson = MAPPER.writeValueAsString(MAPPER.readTree(
            getClass().getClassLoader().getResourceAsStream("toaster-document/device-toaster.json")));
        JSONAssert.assertEquals(expectedJson, jsonDeviceDoc, IGNORE_ORDER);
    }
}
