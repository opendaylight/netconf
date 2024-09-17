/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

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
import javax.net.ssl.SSLException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

class MountPointE2ETest extends AbstractE2ETest {
    private static final YangModuleInfo DEVICE_YANG_MODEL =
        org.opendaylight.yang.svc.v1.test.device.simulator.rev240917.YangModuleInfoImpl.getInstance();
    private static final String DEVICE_USERNAME = "device-username";
    private static final String DEVICE_PASSWORD = "device-password";
    private static final String TOPOLOGY_URI =
        "/rests/data/network-topology:network-topology/topology=topology-netconf";
    private static final String DEVICE_NODE_URI = TOPOLOGY_URI + "/node=device-sim";
    private static final String DEVICE_STATUS_URI =
        DEVICE_NODE_URI + "/netconf-node-topology:netconf-node?fields=connection-status";
    private static final String DEVICE_MOUNT_URI = DEVICE_NODE_URI + "/yang-ext:mount";
    private static final String DEVICE_DATA_ROOT_URI = DEVICE_MOUNT_URI + "/device-sim:data-root";

    @TempDir
    private File tmpDir;
    private NetconfTopologyImpl topologyService;
    private int devicePort;
    private NetconfDeviceSimulator deviceSimulator;

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();

        // topology
        final var dataBroker = getDataBroker();
        final var netconfTimer = new DefaultNetconfTimer();
        final var encryptionService = new TestEncryptionService();
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

        // device simulator
        devicePort = freePort();
        final var config = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setModels(Set.of(DEVICE_YANG_MODEL))
            .setMdSal(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .build();
        deviceSimulator = new NetconfDeviceSimulator(config);
        deviceSimulator.start();
    }

    @AfterEach
    @Override
    void afterEach() {
        if (deviceSimulator != null) {
            deviceSimulator.close();
        }
        if (topologyService != null) {
            topologyService.close();
            topologyService = null;
        }
        super.afterEach();
    }

    @Test
    void dataCRUDJson() throws Exception {
        mountDeviceJson();

        // create
        var initialData = """
            {
                "device-sim:data-root": {
                    "name": "device",
                    "properties": [{
                        "id": "id1",
                        "name": "name1",
                        "value": "value1"
                    }]
                }
            }""";
        var response = invokeRequest(HttpMethod.POST, DEVICE_MOUNT_URI, APPLICATION_JSON, initialData);
        assertEquals(HttpResponseStatus.CREATED, response.status());
        assertContentJson(DEVICE_DATA_ROOT_URI, initialData);

        // update (merge)
        response = invokeRequest(HttpMethod.PATCH, DEVICE_DATA_ROOT_URI, APPLICATION_JSON, """
            {
                "device-sim:data-root": {
                    "properties" : [{
                        "id" : "id1",
                        "value" : "value-updated"
                    }]
                }
            }""");
        assertEquals(HttpResponseStatus.OK, response.status());
        assertContentJson(DEVICE_DATA_ROOT_URI, """
            {
                "device-sim:data-root": {
                    "name": "device",
                    "properties": [{
                        "id": "id1",
                        "name": "name1",
                        "value": "value-updated"
                    }]
                }
            }""");

        // replace
        final var replaceData = """
            {
                "device-sim:data-root": {
                    "properties": [{
                        "id": "id2",
                        "name": "name2",
                        "value": "value2"
                    }]
                }
            }""";
        response = invokeRequest(HttpMethod.PUT, DEVICE_DATA_ROOT_URI, APPLICATION_JSON, replaceData);
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        assertContentJson(DEVICE_DATA_ROOT_URI, replaceData);

        // delete
        response = invokeRequest(HttpMethod.DELETE, DEVICE_DATA_ROOT_URI);
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        // validate deleted
        response = invokeRequest(HttpMethod.GET, DEVICE_DATA_ROOT_URI);
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    private void mountDeviceJson() throws Exception {
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
        //{
        //  "netconf-node-topology:netconf-node": {
        //    "connection-status": "connected"
        //  }
        //}
        final var status = json.getJSONObject("netconf-node-topology:netconf-node").getString("connection-status");
        return "connected".equals(status);
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
