/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringbootConfigLoaderTest {
    private static final int BIND_PORT = 8182;
    private static final String API_ROOT_PATH = "restconf";
    private static final int BOSS_THREADS = 0;
    private static final List<String> CIPHERS = List.of("aes256-ctr", "aes192-ctr", "aes128-ctr");
    private static final int HTTP2_MAX_FRAME_SIZE = 16384;
    private static final String TLS_CERTIFICATE = "etc/tls/cert.pem";
    private static final String TLS_PRIVATE_KEY = "etc/tls/key.pem";

    private final ConfigLoader configLoader = new SpringbootConfigLoader();

    @Test
    void loadYamlFileTest() {
        final var alfaConfig = configLoader.getConfig(Alfa.class, "resource.alfa", "config.yaml");
        assertEquals(BIND_PORT, alfaConfig.bindPort);
        assertEquals(API_ROOT_PATH, alfaConfig.apiRootPath);
        assertEquals(BOSS_THREADS, alfaConfig.bossThreads);
        assertEquals(CIPHERS, alfaConfig.ciphers);
        final var betaConfig = configLoader.getConfig(Beta.class, "resource.beta", "config.yaml");
        assertEquals(HTTP2_MAX_FRAME_SIZE, betaConfig.http2MaxFrameSize);
        assertEquals(TLS_CERTIFICATE, betaConfig.tlsCertificate);
        assertEquals(TLS_PRIVATE_KEY, betaConfig.tlsPrivateKey);
    }

    @Test
    void loadJavaPropertiesFile() {
        final var alfaConfig = configLoader.getConfig(Alfa.class, "", "resource.alfa.cfg");
        assertEquals(BIND_PORT, alfaConfig.bindPort);
        assertEquals(API_ROOT_PATH, alfaConfig.apiRootPath);
        assertEquals(BOSS_THREADS, alfaConfig.bossThreads);
        assertEquals(CIPHERS, alfaConfig.ciphers);
        final var betaConfig = configLoader.getConfig(Beta.class, "", "resource.beta.cfg");
        assertEquals(HTTP2_MAX_FRAME_SIZE, betaConfig.http2MaxFrameSize);
        assertEquals(TLS_CERTIFICATE, betaConfig.tlsCertificate);
        assertEquals(TLS_PRIVATE_KEY, betaConfig.tlsPrivateKey);
    }

    record Alfa(int bindPort, String apiRootPath, int bossThreads, List<String> ciphers) {}

    record Beta(int http2MaxFrameSize, String tlsCertificate, String tlsPrivateKey) {}
}
