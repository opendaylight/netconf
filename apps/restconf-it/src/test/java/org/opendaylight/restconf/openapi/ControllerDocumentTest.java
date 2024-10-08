/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.restconf.server.netty.AbstractE2ETest;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class ControllerDocumentTest extends AbstractE2ETest {
    private static final String BASE_PATH = "/openapi";
    private static final String API_V3_PATH = BASE_PATH + "/api/v3";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOASTER = "toaster";
    private static final String TOASTER_REV = "2009-11-20";
    /**
     * Model toaster@2009-11-19 is used for test correct generating of openapi with models with same name and another
     * revision date. We want to test that the same model is not duplicated and loaded just the newest version.
     */
    private static final String TOASTER_OLD_REV = "2009-11-19";

//    private OpenApiService openApiService;

    protected void initializeClass(final String yangPath){
//        final var context = YangParserTestUtils.parseYangResourceDirectory(yangPath);
//        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService, domMountPointService,
//            "rests");
//        final var openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService, "rests");
    }

    @BeforeEach
    void beforeEach() {
        initializeClass("/netty-documents/");
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
