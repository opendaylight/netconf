/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.client.NetconfClientFactoryImpl;
import org.opendaylight.netconf.client.SslContextFactory;
import org.opendaylight.netconf.client.mdsal.DeviceActionFactoryImpl;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultSchemaResourceManager;
import org.opendaylight.netconf.common.impl.DefaultNetconfTimer;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactoryImpl;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.restconf.server.netty.NullAAAEncryptionService;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class MountPointDocumentTest extends AbstractOpenApiTest {
    private static final YangModuleInfo TOASTER_YANG_MODEL =
        org.opendaylight.yang.svc.v1.http.netconfcentral.org.ns.toaster.rev091120.YangModuleInfoImpl.getInstance();
    private static final YangModuleInfo TOASTER_OLD_YANG_MODEL =
        org.opendaylight.yang.svc.v1.http.netconfcentral.org.ns.toaster.rev091119.YangModuleInfoImpl.getInstance();
    private static final String DEVICE_USERNAME = "device-username";
    private static final String DEVICE_PASSWORD = "device-password";
    private static final String TOPOLOGY_URI =
        "/rests/data/network-topology:network-topology/topology=topology-netconf";
    private static final String DEVICE_NODE_URI = TOPOLOGY_URI + "/node=device-sim";
    private static final String DEVICE_STATUS_URI =
        DEVICE_NODE_URI + "/netconf-node-topology:netconf-node?fields=connection-status";

    @TempDir
    private File tmpDir;
    private int devicePort;
    private NetconfDeviceSimulator deviceSimulator;
    private NetconfTopologyImpl topologyService;

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();

        // topology
        final var dataBroker = getDataBroker();
        final var netconfTimer = new DefaultNetconfTimer();
        final var encryptionService = new NullAAAEncryptionService();
        final var netconfClientConfBuilderFactory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService,
            id -> null, sslContextFactoryProvider());
        final var netconfClientFactory = new NetconfClientFactoryImpl(netconfTimer, sshTransportStackFactory);
        final var topologySchemaAssembler = new NetconfTopologySchemaAssembler(1, 4, 1L, TimeUnit.MINUTES);
        final var yangParserFactory = new DefaultYangParserFactory();
        final var schemaSourceMgr =
            new DefaultSchemaResourceManager(yangParserFactory, tmpDir.getAbsolutePath(), "schema");
        final var baseSchemaProvider = new DefaultBaseNetconfSchemaProvider(yangParserFactory);

        topologyService = new NetconfTopologyImpl(netconfClientFactory, netconfTimer, topologySchemaAssembler,
            schemaSourceMgr, dataBroker, domMountPointService, encryptionService, netconfClientConfBuilderFactory,
            rpcProviderService, baseSchemaProvider, new DeviceActionFactoryImpl());
    }

    @AfterEach
    @Override
    void afterEach() throws Exception {
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

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1' endpoint.
     */
    @Test
    void getMountDocTest() throws Exception {
        startDeviceSimulator();
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
        startDeviceSimulator();
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

    /**
     * Tests the swagger document that is result of the call to the '/mounts' endpoint.
     */
    @Test
    void getMountsTest() throws Exception {
        startDeviceSimulator();
        mountDeviceJson();
        assertContentJson(API_V3_PATH + "/mounts", """
            [
                {
                    "instance": "/network-topology:network-topology/topology=topology-netconf/node=device-sim/",
                    "id": "1"
                }
            ]""");
    }

    protected static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            MountPointDocumentTest.class.getClassLoader().getResourceAsStream(jsonPath)));
    }

    protected void mountDeviceJson() throws Exception {
        // validate topology node is defined
        assertContentJson(TOPOLOGY_URI,
            """
                {
                    "network-topology:topology": [{
                        "topology-id":"topology-netconf"
                    }]
                }""");
        final var input = """
            {
               "network-topology:node": [{
                   "node-id": "device-sim",
                   "netconf-node-topology:netconf-node": {
                       "host": "%s",
                       "port": %d,
                       "login-password-unencrypted": {
                           "username": "%s",
                           "password": "%s"
                       },
                       "tcp-only": false
                   }
               }]
            }
            """.formatted(localAddress, devicePort, DEVICE_USERNAME, DEVICE_PASSWORD);
        final var response = invokeRequest(HttpMethod.POST, TOPOLOGY_URI, APPLICATION_JSON, input);
        assertEquals(HttpResponseStatus.CREATED, response.status());
        // wait till connected
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(500))
            .until(this::deviceConnectedJson);
    }

    private boolean deviceConnectedJson() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, DEVICE_STATUS_URI);
        assertEquals(HttpResponseStatus.OK, response.status());
        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        final var status = json.getJSONObject("netconf-node-topology:netconf-node").getString("connection-status");
        return "connected".equals(status);
    }

    protected void startDeviceSimulator() {
        devicePort = 17830;
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

    private static SslContextFactoryProvider sslContextFactoryProvider() {
        // no TLS used in test -- provide default non-empty
        return specification -> (SslContextFactory) allowedKeys -> {
            try {
                return SslContextBuilder.forClient().build();
            } catch (SSLException e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
