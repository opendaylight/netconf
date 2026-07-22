/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.restconf.it.ProtocolVersion;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class MountPointDocumentTest extends AbstractOpenApiTest {
    private static final YangModuleInfo TOASTER_YANG_MODEL =
        org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterData.META.moduleInfo();
    private static final YangModuleInfo TOASTER_OLD_YANG_MODEL =
        org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091119.ToasterData.META.moduleInfo();

    private int devicePort;
    private NetconfDeviceSimulator deviceSimulator;
    private NetconfTopologyImpl topologyService;

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        super.beforeEach();
        // setting up topology
        topologyService = setupTopology();

        // starting simulator and mounting device
        startDeviceSimulator();
        mountDeviceJson(devicePort);
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
    @Disabled("NETCONF-1566: HTTPClient content length limitation")
    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void getMountDocTest(final ProtocolVersion version) throws Exception {
        final var expectedJson = getExpectedDoc("openapi-documents/device-all.json");

        final var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/mounts/1", version);
        assertEquals(HttpResponseStatus.OK, response.status());

        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(fillPort(expectedJson, port(), schemeOf(version)), resultDoc,
            JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster@revision' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    void getMountDocByModuleTest(final String revision, final String jsonPath, final ProtocolVersion version)
            throws Exception {
        final var expectedJson = getExpectedDoc("openapi-documents/" + jsonPath);
        final var uri = API_V3_PATH + "/mounts/1/" + TOASTER + "?revision=" + revision;

        final var response = invokeRequest(HttpMethod.GET, uri, version);
        assertEquals(HttpResponseStatus.OK, response.status());

        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(fillPort(expectedJson, port(), schemeOf(version)), resultDoc,
            JSONCompareMode.NON_EXTENSIBLE);
    }

    private static Stream<Arguments> getMountDocByModuleTest() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(TOASTER_REV, "device-toaster.json"),
            Arguments.of(TOASTER_OLD_REV, "device-toaster-old.json")
        ).flatMap(base -> Stream.of(ProtocolVersion.values())
            .map(version -> Arguments.of(base.get()[0], base.get()[1], version)));
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts' endpoint.
     */
    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void getMountsTest(final ProtocolVersion version) throws Exception {
        assertContentJson(API_V3_PATH + "/mounts", """
            [
                {
                    "instance": "/network-topology/topology=topology-netconf/node=device-sim/",
                    "id": "1"
                }
            ]""", version);
    }

    private static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            MountPointDocumentTest.class.getClassLoader().getResourceAsStream(jsonPath)));
    }

    private void startDeviceSimulator() {
        devicePort = randomBindablePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .setMdSal(true)
            .setModels(Set.of(TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL));
        deviceSimulator = new NetconfDeviceSimulator(configBuilder.build());
        deviceSimulator.start();
    }
}
