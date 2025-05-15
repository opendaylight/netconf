/*
 * Copyright (c) 2025 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;

public class NC1440Test extends AbstractOpenApiTest {
    private NetconfDeviceSimulator deviceSimulator;
    private NetconfTopologyImpl topologyService;
    private HttpClient client;
    private int devicePort;

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
        // topology
        topologyService = setupTopology();
        client = HttpClient.newBuilder()
            .authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(USERNAME, PASSWORD.toCharArray());
                }
            })
            .build();
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
        client.close();
        super.afterEach();
    }

    /**
     * Emulates successful mount of the device with next request to OpenApi for that device's schema.
     * This case cover successful query for schema created based on two test models. One of the models hase a leafref
     * that referencing to node in other model.
     */
    @Test
    public void leafrefResolvedTest() throws Exception {
        startDeviceSimulator("target/test-classes/nc1440");
        mountDeviceJson(devicePort);

        final var headers = client.send(HttpRequest.newBuilder()
            .GET()
            .uri(new URI("http://" + host + API_V3_PATH + "/mounts/1"))
            .timeout(Duration.ofSeconds(10))
            .build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(200, headers.statusCode());
    }

    /**
     * Emulates successful mount of the device with next request to OpenApi for that device's schema.
     * This case cover failing query for schema created based on three test models. One of the models hase a leafref
     * that referencing to node in other model. Third model deviates said container in first model and makes it
     * {@code not-supported} in yang. This deviation excludes container from
     * {@link org.opendaylight.yangtools.yang.model.api.EffectiveModelContext} and leads to absence it from data and
     * schema tree. Resulting context will throw error when OpenApi is trying to resolve leafref type. Said scenario
     * reproduces what happened in NC1440.
     */
    @Test
    public void leafrefNotResolvedTest() throws Exception {
        startDeviceSimulator("target/test-classes/nc1440-deviated");
        mountDeviceJson(devicePort);

        final var exception = assertThrows(HttpTimeoutException.class, () -> {
            client.send(HttpRequest.newBuilder()
                .GET()
                .uri(new URI("http://" + host + API_V3_PATH + "/mounts/1"))
                .timeout(Duration.ofSeconds(10))
                .build(), HttpResponse.BodyHandlers.discarding());
        });
        assertEquals("request timed out", exception.getMessage());
    }

    private void startDeviceSimulator(final String path) {
        devicePort = randomBindablePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .build();

        configBuilder.setSchemasDir(Path.of(path).toFile());
        deviceSimulator = new NetconfDeviceSimulator(configBuilder);
        deviceSimulator.start();
    }
}
