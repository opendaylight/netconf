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
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
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
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl;
import org.opendaylight.restconf.openapi.netty.OpenApiResourceProvider;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class BenchmarkTest extends AbstractE2ETest {
    private static final String HTTP_URL = "http://localhost/path";
    protected static final String BASE_PATH = "/openapi";
    protected static final String API_V3_PATH = BASE_PATH + "/api/v3";
    private static final String DEVICE_USERNAME = "device-username";
    private static final String DEVICE_PASSWORD = "device-password";
    private static final String TOPOLOGY_URI =
        "/rests/data/network-topology:network-topology/topology=topology-netconf";
    private static final String DEVICE_NODE_URI = TOPOLOGY_URI + "/node=device-sim";
    private static final String DEVICE_STATUS_URI =
        DEVICE_NODE_URI + "/netconf-node-topology:netconf-node?fields=connection-status";
    protected static final String RESTS = "rests";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private NetconfDeviceSimulator deviceSimulator;
    private int devicePort;
    protected OpenApiService openApiService;
    protected OpenApiResourceProvider openApiResourceProvider;

    @TempDir
    private File tmpDir;
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

        // Separate context with only toaster model
        final var openApiSchemaContext = YangParserTestUtils.parseYangResourceDirectory("/juniper/");
        final var openApiSchemaService = new FixedDOMSchemaService(openApiSchemaContext);
        // OpenApi
        final var mountPointOpenApiGeneratorRFC8040 = new MountPointOpenApiGeneratorRFC8040(openApiSchemaService,
            domMountPointService, RESTS);
        openApiService = new OpenApiServiceImpl(mountPointOpenApiGeneratorRFC8040,
            new OpenApiGeneratorRFC8040(openApiSchemaService, RESTS));
        openApiResourceProvider = new OpenApiResourceProvider(openApiService);
        endpoint.registerWebResource(openApiResourceProvider);
    }

    @Test
    public void getMountDocTest() throws Exception {
        startDeviceSimulator();
        mountDeviceJson();
        final var client = HttpClient.newBuilder()
            .authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        "username",
                        "pa$$w0Rd".toCharArray());
                }
            })
            .build();
        final var request = HttpRequest.newBuilder()
            .uri(new URI("http://" + host + API_V3_PATH + "/mounts/1"))
            .GET()
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(1357417988, response.body().getBytes().length);
        // compare start of file? and end of file?

        response = client.send(HttpRequest.newBuilder()
                .GET()
                .uri(new URI("http://" + host + API_V3_PATH + "/mounts/1?depth=1&width=1"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(9198037, response.body().getBytes().length);
    }

    private void startDeviceSimulator() {
        // mdsal = true --> settable mode, mdsal datastore
        // mdsal = false --> simulated mode, data is taken from conf files
        devicePort = randomBindablePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .build();

        configBuilder.setSchemasDir(new File("target/test-classes/juniper"));
        deviceSimulator = new NetconfDeviceSimulator(configBuilder);
        deviceSimulator.start();
    }

    void mountDeviceJson() throws Exception {
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
        await().atMost(Duration.ofSeconds(50)).pollInterval(Duration.ofMillis(5000))
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
