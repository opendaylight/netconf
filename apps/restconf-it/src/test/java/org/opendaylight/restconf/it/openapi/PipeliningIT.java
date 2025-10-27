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
            socket.setSoTimeout(3000);
            // Prepare requests
            final var req1 = String.format("""
                GET /openapi/api/v3/mounts/1 HTTP/1.1
                Host: %s
                Authorization: Basic dXNlcm5hbWU6cGEkJHcwUmQ=\n
                """, host).replace("\n", "\r\n");

            final var req2 = String.format("""
                GET /openapi/api/v3/mounts/1?depth=1&width=1 HTTP/1.1
                Host: %s
                Authorization: Basic dXNlcm5hbWU6cGEkJHcwUmQ=\n
                """, host).replace("\n", "\r\n");

            // Send requests
            final var out = socket.getOutputStream();
            out.write(req1.getBytes(StandardCharsets.UTF_8));
            out.write(req2.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Collect responses
            final var firstResponseSize = readChunkedResponse(socket.getInputStream());
            final var secondResponseSize = readChunkedResponse(socket.getInputStream());

            // Assert that responses are in order in which requests were send
            assertEquals(1357435766, firstResponseSize);
            assertEquals(9213921, secondResponseSize);
        }
    }

    private static int readChunkedResponse(final InputStream in) throws Exception {
        // Read headers
        final var headersBuilder = new StringBuilder();
        int byteRead;
        while (!headersBuilder.toString().contains("\r\n\r\n") && (byteRead = in.read()) != -1) {
            headersBuilder.append((char) byteRead);
        }
        final var headers = headersBuilder.toString();
        assertFalse(headers.isEmpty());
        assertTrue(headers.contains("HTTP/1.1 200 OK"));
        assertTrue(headers.contains("transfer-encoding: chunked"));

        var totalSize = 0;

        while (true) {
            // Read chunk size line
            final var line = readLine(in);

            // Extract chunk size
            var chunkSize = Integer.parseInt(line.trim(), 16);
            if (chunkSize == 0) {
                // Final chunk - consume the trailing CRLF after "0" line
                readLine(in);
                break;
            }
            totalSize += chunkSize;

            // Read and discard chunk data
            while (chunkSize > 0) {
                final var skipped = in.skip(chunkSize);
                if (skipped == 0) {
                    throw new IllegalStateException("Unexpected end of stream in chunk data");
                }
                chunkSize -= (int) skipped;
            }

            // Consume CRLF
            readLine(in);
        }
        return totalSize;
    }

    private static String readLine(final InputStream in) throws Exception {
        final var line = new StringBuilder();
        int byteRead;
        var prevByte = -1;
        while ((byteRead = in.read()) != -1) {
            if (prevByte == '\r' && byteRead == '\n') {
                break;
            } else {
                line.append((char) byteRead);
                prevByte = byteRead;
            }
        }
        return line.substring(0, line.length() - 1);
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
