/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.restconf;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.dagger.DaggerRestconfNetconfFactory;
import org.opendaylight.netconf.dagger.RestconfNetconfFactory;

class RestconfNettyEndpointTest {
    private RestconfNetconfFactory component;

    @BeforeEach
    void setUp() {
        component = DaggerRestconfNetconfFactory.create();
    }

    @AfterEach
    void tearDown() {
        if (component != null) {
            component.close();
        }
    }

    @Test
    void testCorrectlyInitializedServices() {
        assertNotNull(component.nettyEndpoint());
        assertNotNull(component.netconfTopologyImpl());
    }

    @Test
    void testNettyEndpointRespondsToHttp() throws Exception {
        assertNotNull(component.nettyEndpoint(), "NettyEndpoint should be initialized");

        // Create Java HTTP Client
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            // Send Get request
            final var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8182/restconf/data"))
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .header("Accept", "application/json")
                .GET()
                .build();
            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Verify the server is correctly responding.
            final var status = response.statusCode();
            assertEquals(200, status, "Expected 200 from Netty, but got HTTP " + status);
        }
    }

    @Test
    void testNetconfDeviceMountAndTopologyIntegration() throws Exception {
        // Initialize Dagger components.
        assertNotNull(component.nettyEndpoint(), "NettyEndpoint should be initialized");
        assertNotNull(component.netconfTopologyImpl(), "Netconf topology should be initialized");

        // Create Java HTTP Client
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            // Send PUT request to create device.
            final var jsonConfig = """
                {
                    "node": [
                        {
                            "node-id": "test-device",
                            "netconf-node": {
                                "netconf-node-topology:port": 17830,
                                "netconf-node-topology:reconnect-on-changed-schema": false,
                                "netconf-node-topology:connection-timeout-millis": 20000,
                                "netconf-node-topology:tcp-only": false,
                                "netconf-node-topology:max-connection-attempts": 3,
                                "netconf-node-topology:login-password-unencrypted": {
                                    "netconf-node-topology:username": "netconf",
                                    "netconf-node-topology:password": "netconf"
                                },
                                "netconf-node-topology:host": "127.0.0.1",
                                "netconf-node-topology:min-backoff-millis": 2000,
                                "netconf-node-topology:max-backoff-millis": 1800000,
                                "netconf-node-topology:backoff-multiplier": 1.5,
                                "netconf-node-topology:keepalive-delay": 120
                            }
                        }
                    ]
                }
                """;
            final var configUri = URI.create("http://localhost:8182/restconf/data/network-topology:network-topology"
                + "/topology=topology-netconf/node=test-device");
            final var putRequest = HttpRequest.newBuilder()
                .uri(configUri)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonConfig))
                .build();
            final var putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());

            // Verify the device configuration was successfully accepted.
            final var putStatus = putResponse.statusCode();
            assertEquals(201, putStatus, "Failed to configure device. Expected HTTP 201, got " + putStatus
                + ". Body: " + putResponse.body());

            // Get operational data.
            final var operUri = URI.create("http://localhost:8182/restconf/data/network-topology:network-topology"
                    + "/topology=topology-netconf/node=test-device?content=nonconfig");
            final var getRequest = HttpRequest.newBuilder()
                .uri(operUri)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .header("Accept", "application/json")
                .GET()
                .build();

            // Verify attempting to connect device.
            await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    final var response = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

                    assertEquals(200, response.statusCode(), "Waiting for operational state to appear...");
                    assertTrue(response.body().contains("\"connection-status\":\"connecting\""),
                        "Operational state found, but missing connection status");
                });
        }
    }
}
