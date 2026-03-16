/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.restconf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestconfNettyEndpointTest {
    private RestconfNettyEndpointTestFactory component;

    @BeforeEach
    void setUp() {
        component = DaggerRestconfNettyEndpointTestFactory.create();
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
    }

    @Test
    void testNettyEndpointRespondsToHttp() throws Exception {
        assertNotNull(component.nettyEndpoint(), "NettyEndpoint should be initialized");

        // Create Java HTTP Client
        final var client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

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
