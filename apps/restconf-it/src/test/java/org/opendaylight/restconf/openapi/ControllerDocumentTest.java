/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
//import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
//import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
//import org.opendaylight.restconf.openapi.impl.OpenApiGeneratorRFC8040;
//import org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl;
//import org.opendaylight.restconf.openapi.netty.OpenApiResourceProvider;
import org.opendaylight.restconf.server.netty.AbstractE2ETest;
//import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class ControllerDocumentTest extends AbstractE2ETest {

    @BeforeEach
    void beforeEach() {
//        final var schemaContext = YangParserTestUtils.parseYangResourceDirectory("/toaster/");
//        final var schemaService = new FixedDOMSchemaService(schemaContext);
//
//        // OpenApi
//        final var mountPointOpenApiGeneratorRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService,
//            domMountPointService, RESTS);
//        openApiService = new OpenApiServiceImpl(mountPointOpenApiGeneratorRFC8040,
//            new OpenApiGeneratorRFC8040(schemaService, RESTS));
//        openApiResourceProvider = new OpenApiResourceProvider(openApiService);
//        endpoint.registerWebResource(openApiResourceProvider);
    }

    @Test
    void controllerAllDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("netty-documents/controller-all.json");

        final var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/single");
        assertEquals(HttpResponseStatus.OK, response.status());

        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster@revision' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    void getDocByModuleTest(final String revision, final String jsonPath) throws Exception {
        final var expectedJson = getExpectedDoc("netty-documents/" + jsonPath);
        var uri = API_V3_PATH + "/" + TOASTER + "?revision=" + revision;

        final var response = invokeRequest(HttpMethod.GET, uri);
        assertEquals(HttpResponseStatus.OK, response.status());

        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    private static Stream<Arguments> getDocByModuleTest() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(TOASTER_REV, "controller-toaster.json"),
            Arguments.of(TOASTER_OLD_REV, "controller-toaster-old.json")
        );
    }

    protected static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            ControllerDocumentTest.class.getClassLoader().getResourceAsStream(jsonPath)));
    }
}
