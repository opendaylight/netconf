/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class ControllerDocumentHttp2Test extends AbstractOpenApiHttp2Test {
    @Test
    void controllerAllDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("openapi-documents/controller-all.json");

        final var response = http2Client.send(HttpRequest.newBuilder()
            .GET()
            .uri(new URI("http://" + host + API_V3_PATH + "/single"))
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

        final var resultDoc = response.body();
        JSONAssert.assertEquals(fillPort(expectedJson, port), resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster@revision' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    void getDocByModuleTest(final String revision, final String jsonPath) throws Exception {
        final var expectedJson = getExpectedDoc("openapi-documents/" + jsonPath);
        final var uri = API_V3_PATH + "/" + TOASTER + "?revision=" + revision;

        final var response = http2Client.send(HttpRequest.newBuilder()
            .GET()
            .uri(new URI("http://" + host + uri))
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

        final var resultDoc = response.body();
        JSONAssert.assertEquals(fillPort(expectedJson, port), resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    private static Stream<Arguments> getDocByModuleTest() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(TOASTER_REV, "controller-toaster.json"),
            Arguments.of(TOASTER_OLD_REV, "controller-toaster-old.json")
        );
    }

    private static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            ControllerDocumentHttp2Test.class.getClassLoader().getResourceAsStream(jsonPath)));
    }
}
