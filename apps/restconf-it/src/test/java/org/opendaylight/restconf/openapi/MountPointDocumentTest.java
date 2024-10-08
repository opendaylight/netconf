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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.restconf.server.netty.MountPointE2ETest;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class MountPointDocumentTest extends MountPointE2ETest {
    private static final YangModuleInfo TOASTER_YANG_MODEL =
        org.opendaylight.yang.svc.v1.http.netconfcentral.org.ns.toaster.rev091120.YangModuleInfoImpl.getInstance();
    private static final YangModuleInfo TOASTER_OLD_YANG_MODEL =
        org.opendaylight.yang.svc.v1.http.netconfcentral.org.ns.toaster.rev091119.YangModuleInfoImpl.getInstance();

    @Test
    void getMountDocTest() throws Exception {
        startDeviceSimulator(true, TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL);
        mountDeviceJson();
        final var expectedJson = getExpectedDoc("netty-documents/device-all.json");

        final var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/mounts/1");
        assertEquals(HttpResponseStatus.OK, response.status());

        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster@revision' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    void getMountDocByModuleTest(final String revision, final String jsonPath) throws Exception {
        startDeviceSimulator(true, TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL);
        mountDeviceJson();
        final var expectedJson = getExpectedDoc("netty-documents/" + jsonPath);
        var uri = API_V3_PATH + "/mounts/1/" + TOASTER + "?revision=" + revision;

        final var response = invokeRequest(HttpMethod.GET, uri);
        assertEquals(HttpResponseStatus.OK, response.status());

        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    private static Stream<Arguments> getMountDocByModuleTest() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(TOASTER_REV, "device-toaster.json"),
            Arguments.of(TOASTER_OLD_REV, "device-toaster-old.json")
        );
    }

    @Test
    void getMountsTest() throws Exception {
        startDeviceSimulator(true, TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL);
        mountDeviceJson();
        assertContentJson(API_V3_PATH + "/mounts", """
            [
                {
                    "instance": "/network-topology/topology=topology-netconf/node=device-sim/",
                    "id": "1"
                }
            ]""");
    }

    protected static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            MountPointDocumentTest.class.getClassLoader().getResourceAsStream(jsonPath)));
    }
}
