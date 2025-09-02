/*
 * Copyright (c) 2025 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;

class PipeliningIT extends AbstractOpenApiTest {
    private NetconfDeviceSimulator deviceSimulator;
    private NetconfTopologyImpl topologyService;
    private int devicePort;

    @BeforeEach
    @Override
    void beforeEach() throws Exception {
        super.beforeEach();
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

    /**
     * Tests HTTP1 pipelining by sending two requests on the same connection:
     * - the first request produces a large response,
     * - the second request produces a much smaller response.
     *
     * <p>Even though the small response is faster to prepare, HTTP/1 requires that
     * responses are returned strictly in the order the requests were received.
     * Therefore, we expect the small response to arrive only after the large one
     * has been fully sent.
     */
    @Test
    void pipelineTest() throws Exception {
        startDeviceSimulator();
        mountDeviceJson(devicePort);

        try (var socket = new Socket("127.0.0.1", port)) {
            // Prepare requests
            final var req1 = String.format("""
                GET /openapi/api/v3/mounts/1?depth=1&width=1 HTTP/1.1
                Host: %s
                Authorization: Basic dXNlcm5hbWU6cGEkJHcwUmQ=\n
                """, host).replace("\n", "\r\n");

            final var req2 = String.format("""
                GET /openapi/api/v3/single HTTP/1.1
                Host: %s
                Authorization: Basic dXNlcm5hbWU6cGEkJHcwUmQ=\n
                """, host).replace("\n", "\r\n");

            // Send requests
            var out = socket.getOutputStream();
            out.write(req1.getBytes(StandardCharsets.US_ASCII));
            out.write(req2.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            // Collect responses
            final var responseSizes = new ArrayList<>();
            for (var i = 0; i < 2; i++) {
                responseSizes.add(processResponse(socket.getInputStream()));
            }

            // Assert that responses are in order in which requests were send
            assertEquals("9198037", responseSizes.getFirst());
            assertEquals("8004", responseSizes.getLast());
        }
    }

    private static String processResponse(final InputStream in) throws Exception {
        final var buffer = new byte[1024];
        final var header = new StringBuilder();
        int read;

        // Read until end of headers
        while (!header.toString().contains("\r\n\r\n") && (read = in.read(buffer)) != -1) {
            header.append(new String(buffer, 0, read, StandardCharsets.US_ASCII));
        }
        assertFalse(header.isEmpty(), "Header is empty");

        final var lines = header.toString().split("\r\n");
        assertTrue(lines[0].endsWith("HTTP/1.1 200 OK") && lines[2].contains("content-length:"));
        return lines[2].substring(lines[2].indexOf(':') + 1).trim();
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
