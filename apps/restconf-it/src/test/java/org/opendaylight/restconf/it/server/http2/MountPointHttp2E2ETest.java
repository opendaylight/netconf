/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
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
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactoryImpl;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.it.server.NullAAAEncryptionService;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

class MountPointHttp2E2ETest extends AbstractHttp2E2ETest {
    private static final YangModuleInfo DEVICE_YANG_MODEL =
        org.opendaylight.yang.svc.v1.test.device.simulator.rev240917.YangModuleInfoImpl.getInstance();
    private static final YangModuleInfo NOTIFICATION_MODEL = org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns
        .netconf.notification._1._0.rev080714.YangModuleInfoImpl.getInstance();
    // RFC 6241 base model: urn:ietf:params:xml:ns:netconf:base:1.0
    private static final YangModuleInfo IETF_NETCONF = org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns
        .netconf.base._1._0.rev110601.YangModuleInfoImpl.getInstance();
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
    protected void beforeEach() throws Exception {
        super.beforeEach();

        // topology
        final var dataBroker = getDataBroker();
        final var netconfTimer = new DefaultNetconfTimer();
        final var encryptionService = new NullAAAEncryptionService();
        final var netconfClientConfBuilderFactory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService,
            id -> null, sslContextFactoryProvider());
        final var netconfClientFactory = new NetconfClientFactoryImpl(netconfTimer, sshTransportStackFactory);
        final var topologySchemaAssembler = new NetconfTopologySchemaAssembler(4);
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
    protected void afterEach() throws Exception {
        if (deviceSimulator != null) {
            deviceSimulator.close();
            deviceSimulator = null;
        }
        if (topologyService != null) {
            topologyService.close();
            topologyService = null;
        }
        if (clientStreamService != null) {
            clientStreamService = null;
        }
        if (streamControl != null) {
            streamControl = null;
        }
        super.afterEach();
    }

    @Test
    void dataCRUDJsonTest() throws Exception {
        startDeviceSimulator(true);
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

        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(DEVICE_MOUNT_URI))
            .POST(HttpRequest.BodyPublishers.ofString(initialData))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertEquals(HttpResponseStatus.CREATED.code(), response.statusCode());
        assertContentJson(DEVICE_DATA_ROOT_URI, initialData);

        // update (merge)
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(DEVICE_DATA_ROOT_URI))
            .method("PATCH", HttpRequest.BodyPublishers.ofString("""
            {
                "device-sim:data-root": {
                    "properties" : [{
                        "id" : "id1",
                        "value" : "value-updated"
                    }]
                }
            }"""))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
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
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(DEVICE_DATA_ROOT_URI))
            .PUT(HttpRequest.BodyPublishers.ofString(replaceData))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.NO_CONTENT.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertContentJson(DEVICE_DATA_ROOT_URI, replaceData);

        // delete
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(DEVICE_DATA_ROOT_URI))
            .DELETE()
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.NO_CONTENT.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

        // validate deleted
        response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(DEVICE_DATA_ROOT_URI))
            .GET()
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertErrorResponseJson(response, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Test
    void yangPatchJsonTest() throws Exception {
        startDeviceSimulator(true);
        mountDeviceJson();

        // CRUD
        var body = """
            {
                "ietf-yang-patch:yang-patch" : {
                    "patch-id" : "patch1",
                    "edit" : [
                        {
                            "edit-id": "edit1",
                            "operation": "create",
                            "target": "",
                            "value": {
                                "device-sim:data-root": {
                                    "name": "device",
                                    "properties": [{
                                        "id": "id1",
                                        "name": "name1",
                                        "value": "value1"
                                    }]
                                }
                            }
                        },
                        {
                            "edit-id": "edit2",
                            "operation": "merge",
                            "target": "",
                            "value": {
                                "device-sim:data-root": {
                                    "properties": [{
                                        "id": "id2",
                                        "name": "name2",
                                        "value": "value2"
                                    }]
                                }
                            }
                        },
                        {
                            "edit-id": "edit1",
                            "operation": "delete",
                            "target": "properties=id1"
                        }
                    ]
                }
            }""";
        var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(DEVICE_DATA_ROOT_URI))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .header(HttpHeaderNames.ACCEPT.toString(), MediaTypes.APPLICATION_YANG_DATA_JSON)
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), MediaTypes.APPLICATION_YANG_PATCH_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());

        // read (validate result)
        assertContentJson(DEVICE_DATA_ROOT_URI, """
            {
                "device-sim:data-root": {
                    "name": "device",
                    "properties": [
                        {
                            "id": "id2",
                            "name": "name2",
                            "value": "value2"
                        }
                    ]
                }
            }""");
    }

    private void startDeviceSimulator(final boolean mdsal) throws Exception {
        // mdsal = true --> settable mode, mdsal datastore
        // mdsal = false --> simulated mode, data is taken from conf files
        devicePort = randomBindablePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .setMdSal(mdsal);
        if (mdsal) {
            configBuilder.setModels(Set.of(DEVICE_YANG_MODEL, IETF_NETCONF));
        } else {
            configBuilder
                .setModels(Set.of(DEVICE_YANG_MODEL, IETF_NETCONF, NOTIFICATION_MODEL))
                .setNotificationFile(Path.of(getClass().getResource("/device-sim-notifications.xml").toURI())
                    .toFile());
        }
        deviceSimulator = new NetconfDeviceSimulator(configBuilder.build());
        deviceSimulator.start();
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

        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(TOPOLOGY_URI))
            .POST(HttpRequest.BodyPublishers.ofString(input))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.CREATED.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        // wait till connected
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(500))
            .until(this::deviceConnectedJson);
    }

    private boolean deviceConnectedJson() throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(DEVICE_STATUS_URI))
            .GET()
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        final var json = new JSONObject(response.body(), JSON_PARSER_CONFIGURATION);
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
