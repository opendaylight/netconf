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
import java.io.IOException;
import java.util.Optional;
import javax.ws.rs.core.Response;
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
//        final var oas = new OpenApiServiceImpl(schemaService, mock(DOMMountPointService.class));
    }

    /**
     * Tests the swagger document that is result of the call to the '/single' endpoint
     */
    @Test
    public void getAllModulesDoc() throws Exception {
        final var getAllController = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/single");
        final var controllerDocAll = openApiService.getAllModulesDoc(getAllController);
        final var jsonControllerDoc = MAPPER.valueToTree(controllerDocAll.getEntity());
        assertEquals(Response.Status.OK.getStatusCode(), controllerDocAll.getStatus());
    }

    @Test
    public void testMapper() throws IOException {
        final String json1 = """
            {
                "employee":
                {
                    "id": "1212",
                    "fullName": "John Miles",
                    "age": 34,
                    "skills": ["Java", "C++", "Python"]
                }
            }""";

        final var j1 = MAPPER.readTree(json1);
        final var j2 = MAPPER.readTree(getClass().getClassLoader().getResourceAsStream("swagger-document/test.json"));
        assertEquals(j1, j2);
    }
}
