/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;

public class BenchmarkOpenApiIT extends AbstractOpenApiTest {
    private NetconfDeviceSimulator deviceSimulator;
    private NetconfTopologyImpl topologyService;
    private int devicePort;

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
        // topology
        topologyService = setupTopology();
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

    @Test
    public void benchmarkTest() throws Exception {
        startDeviceSimulator();
        mountDeviceJson(devicePort);
        final var client = HttpClient.newBuilder()
            .authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        USERNAME,
                        PASSWORD.toCharArray());
                }
            })
            .build();

        // Due to size of the response, we discard the body.
        final var headers = client.send(HttpRequest.newBuilder()
            .GET()
            .uri(new URI("http://" + host + API_V3_PATH + "/mounts/1"))
            .timeout(Duration.ofMinutes(5))
            .build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(200, headers.statusCode());
        // Mainly here to verify there is some large data in the response, the exact length is not that important
        assertEquals("1357417988", headers.headers().firstValue("content-length").orElseThrow());

        final var response = client.send(HttpRequest.newBuilder()
            .GET()
            .uri(new URI("http://" + host + API_V3_PATH + "/mounts/1?depth=1&width=1"))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        // The response is still too large for whole comparison, so just check some random rpc, to verify the data
        assertTrue(response.body().contains("junos-conf-root:configuration"));
        assertTrue(response.body().contains("junos-rpc-services:get-l2tp-disconnect-cause-summary"));
        assertTrue(response.body().contains("junos-rpc-unified-edge_get-sgw-cac-statistics_input"));
    }

    private void startDeviceSimulator() {
        devicePort = randomBindablePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .build();

        configBuilder.setSchemasDir(Path.of("target/test-classes/juniper").toFile());
        deviceSimulator = new NetconfDeviceSimulator(configBuilder);
        deviceSimulator.start();
    }
}
