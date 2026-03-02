/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;

class BenchmarkOpenApiHttp3IT extends AbstractOpenApiHttp3Test {
    private NetconfDeviceSimulator deviceSimulator;
    private NetconfTopologyImpl topologyService;

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        super.beforeEach();
        // topology
        topologyService = setupTopology();
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
        super.afterEach();
    }

    @Test
    void benchmarkTest() throws Exception {
        mountDeviceJson(startDeviceSimulator());

        // Due to size of the response, we discard the body.
        final var headers = client().send(HttpRequest.newBuilder()
            .GET()
            .uri(createApiUri("/mounts/1"))
            .timeout(Duration.ofMinutes(5))
            .build(), true);

        assertEquals(HttpResponseStatus.OK, headers.status());

        final var response = client().send(HttpRequest.newBuilder()
            .GET()
            .uri(createApiUri("/mounts/1?depth=1&width=1"))
            .build());

        assertEquals(HttpResponseStatus.OK, response.status());
        // The response is still too large for whole comparison, so just check some random rpc, to verify the data
        assertTrue(response.content().contains("junos-conf-root:configuration"));
        assertTrue(response.content().contains("junos-rpc-services:get-l2tp-disconnect-cause-summary"));
        assertTrue(response.content().contains("junos-rpc-unified-edge_get-sgw-cac-statistics_input"));
    }

    private int startDeviceSimulator() {
        final var devicePort = randomBindablePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .build();

        configBuilder.setSchemasDir(Path.of("target/test-classes/juniper").toFile());
        deviceSimulator = new NetconfDeviceSimulator(configBuilder);
        deviceSimulator.start();
        return devicePort;
    }
}
