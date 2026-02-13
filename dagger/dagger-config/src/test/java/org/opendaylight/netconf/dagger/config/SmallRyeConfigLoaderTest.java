/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithName;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SmallRyeConfigLoaderTest {
    private static final int BIND_PORT = 8182;
    private static final String API_ROOT_PATH = "restconf";
    private static final int BOSS_THREADS = 0;
    private static final List<String> CIPHERS = List.of("aes256-ctr", "aes192-ctr", "aes128-ctr");
    private static final int HTTP2_MAX_FRAME_SIZE = 16384;
    private static final String TLS_CERTIFICATE = "etc/tls/cert.pem";
    private static final String TLS_PRIVATE_KEY = "etc/tls/key.pem";
    private static final Path YAML_CONFIG = Path.of("config.yaml");
    private static final Path ALFA_JP_CONFIG = Path.of("config/alfa.cfg");
    private static final Path BETA_JP_CONFIG = Path.of("resource.beta.cfg");

    private final ConfigLoader configLoader = new SmallRyeConfigLoader();

    @Test
    void loadYamlFileTest() {
        final var alfaConfig = configLoader.getConfig(Alfa.class, "resource.alfa", YAML_CONFIG);
        assertEquals(BIND_PORT, alfaConfig.port());
        assertEquals(API_ROOT_PATH, alfaConfig.path());
        assertEquals(BOSS_THREADS, alfaConfig.threads());
        assertEquals(CIPHERS, alfaConfig.ciphersList());
        final var betaConfig = configLoader.getConfig(Beta.class, "resource.beta", YAML_CONFIG);
        assertEquals(HTTP2_MAX_FRAME_SIZE, betaConfig.http2MaxFrameSize());
        assertEquals(TLS_CERTIFICATE, betaConfig.tlsCertificate());
        assertEquals(TLS_PRIVATE_KEY, betaConfig.tlsPrivateKey());
    }

    @Test
    void loadJavaPropertiesFile() {
        final var alfaConfig = configLoader.getConfig(Alfa.class, "resource.alfa", ALFA_JP_CONFIG);
        assertEquals(BIND_PORT, alfaConfig.port());
        assertEquals(API_ROOT_PATH, alfaConfig.path());
        assertEquals(BOSS_THREADS, alfaConfig.threads());
        assertEquals(CIPHERS, alfaConfig.ciphersList());
        final var betaConfig = configLoader.getConfig(Beta.class, "", BETA_JP_CONFIG);
        assertEquals(HTTP2_MAX_FRAME_SIZE, betaConfig.http2MaxFrameSize());
        assertEquals(TLS_CERTIFICATE, betaConfig.tlsCertificate());
        assertEquals(TLS_PRIVATE_KEY, betaConfig.tlsPrivateKey());
    }

    @ConfigMapping(prefix = "resource.alfa")
    public interface Alfa {
        @WithName("bind-port")
        int port();

        @WithName("api-root-path")
        String path();

        @WithName("boss-threads")
        int threads();

        @WithName("ciphers")
        List<String> ciphersList();
    }

    @ConfigMapping(namingStrategy = NamingStrategy.KEBAB_CASE)
    public interface Beta {
        int http2MaxFrameSize();

        String tlsCertificate();

        String tlsPrivateKey();
    }
}
