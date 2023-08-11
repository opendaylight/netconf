/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiApplication;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class ControllerSwaggerDocumentTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private static DOMSchemaService schemaService;

    private HttpServer server;


    @BeforeClass
    public static void beforeClass() {
        schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        when(schemaService.getGlobalContext()).thenReturn(context);
    }

    @Before
    public void before() {
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final MountPointOpenApiGeneratorRFC8040 mountPointRFC8040 =
                new MountPointOpenApiGeneratorRFC8040(schemaService, service);
        final OpenApiGeneratorRFC8040 openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService);
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(INSTANCE_ID);
        final OpenApiService openApiService = new OpenApiServiceImpl(mountPointRFC8040, openApiGeneratorRFC8040);


//        final AbstractBinder binder = new AbstractBinder() {
//            @Override
//            protected void configure() {
//                bind(openApiService).to(OpenApiService.class);
//            }
//        };

        final ResourceConfig resConfig = ResourceConfig.forApplication(new OpenApiApplication(openApiService))
//            .register(binder)
            .property(ServerProperties.TRACING, "ALL")
            .property(ServerProperties.TRACING_THRESHOLD, "VERBOSE")
            .property(ServerProperties.MONITORING_ENABLED, "true");
        final URI uri = UriBuilder.fromUri("http://localhost/").port(8181).build();
        server = GrizzlyHttpServerFactory.createHttpServer(uri, resConfig);
    }

    @After
    public void tearDown() {
        server.shutdownNow();
    }


    @Test
    public void testOpenApiServiceImplCreation() {
        WebTarget getControllerDocument = ClientBuilder.newClient().target("http://localhost:8181");
        Response controllerRes = getControllerDocument.path("single").request().get();
        assertNotNull(controllerRes);
        assertNotEquals(Response.Status.NOT_FOUND.getStatusCode(), controllerRes.getStatus());
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
