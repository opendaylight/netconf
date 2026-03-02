/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpRequest;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class MountPointDocumentHttp3Test extends AbstractOpenApiHttp3Test {
    private static final YangModuleInfo TOASTER_YANG_MODEL =
        org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterData.META.moduleInfo();
    private static final YangModuleInfo TOASTER_OLD_YANG_MODEL =
        org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091119.ToasterData.META.moduleInfo();

    private NetconfDeviceSimulator deviceSimulator;
    private NetconfTopologyImpl topologyService;

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        super.beforeEach();
        // setting up topology
        topologyService = setupTopology();

        // starting simulator and mounting device
        mountDeviceJson(startDeviceSimulator());
    }

    @AfterEach
    @Override
    protected void afterEach() throws Exception {
        if (deviceSimulator != null) {
            deviceSimulator.close();
            deviceSimulator = null;
        }
        if (topologyService != null) {
            topologyService.close();
            topologyService = null;
        }
        super.afterEach();
    }

    // FIXME NETCONF-1566: Fails because of HTTPClient maximum content length limitation. Enable after fix.
    /**
     * Tests the swagger document that is result of the call to the '/mounts/1' endpoint.
     */
    @Disabled
    @Test
    void getMountDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("openapi-documents/device-all.json");

        final var response = client().send(HttpRequest.newBuilder()
            .GET()
            .uri(createApiUri("/mounts/1"))
            .build());

        assertEquals(HttpResponseStatus.OK, response.status());
        final var resultDoc = response.content();
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Verifies that the OpenAPI document returned by the /mounts/1/toaster endpoint for a given revision
     * matches the expected JSON structure.
     */
    @ParameterizedTest
    @MethodSource
    void getMountDocByModuleTest(final String revision, final String jsonPath) throws Exception {
        final var expectedJson = getExpectedDoc("openapi-documents/" + jsonPath);
        final var uri = "/mounts/1/" + TOASTER + "?revision=" + revision;

        final var response = client().send(HttpRequest.newBuilder()
            .GET()
            .uri(createApiUri(uri))
            .build());

        assertEquals(HttpResponseStatus.OK, response.status());
        final var resultDoc = response.content();
        JSONAssert.assertEquals(fillPort(expectedJson, port, "https"), resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    private static Stream<Arguments> getMountDocByModuleTest() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(TOASTER_REV, "device-toaster.json"),
            Arguments.of(TOASTER_OLD_REV, "device-toaster-old.json")
        );
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts' endpoint.
     */
    @Test
    void getMountsTest() throws Exception {
        assertContentJson(API_V3_PATH + "/mounts", """
            [
                {
                    "instance": "/network-topology/topology=topology-netconf/node=device-sim/",
                    "id": "1"
                }
            ]""");
    }

    private static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            MountPointDocumentHttp3Test.class.getClassLoader().getResourceAsStream(jsonPath)));
    }

    private int startDeviceSimulator() {
        final var devicePort = randomBindablePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .setModels(Set.of(TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL));
        deviceSimulator = new NetconfDeviceSimulator(configBuilder.build());
        deviceSimulator.start();
        return devicePort;
    }
}
