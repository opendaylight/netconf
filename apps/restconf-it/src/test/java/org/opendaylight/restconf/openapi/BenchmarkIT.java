/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;

public class BenchmarkIT extends AbstractOpenApiTest {
    private NetconfDeviceSimulator deviceSimulator;
    private int devicePort;
    private NetconfTopologyImpl topologyService;

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
        final var request = HttpRequest.newBuilder()
            .uri(new URI("http://" + host + API_V3_PATH + "/mounts/1"))
            .GET()
            .timeout(Duration.ofMinutes(5))
            .build();

        // Due to size of the response, we discard the body and only read headers.
        final var headers = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertEquals(200, headers.statusCode());
        // Mainly here to verify there is some data in the response
        assertEquals("1357417987", headers.headers().firstValue("content-length").orElseThrow());
        // compare start of file? and end of file?

        final var response = client.send(HttpRequest.newBuilder()
                .GET()
                .uri(new URI("http://" + host + API_V3_PATH + "/mounts/1?depth=1&width=1"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(9198036, response.body().getBytes().length);
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

    /**
     * Find a local port which has a good chance of not failing {@code bind()} due to a conflict.
     *
     * @return a local port
     */
    private static int randomBindablePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
