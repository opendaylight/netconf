/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.config;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.microprofile.config.spi.ConfigSource;

@Singleton
@NonNullByDefault
public final class SmallRyeConfigLoader implements ConfigLoader {

    @Override
    public <T> T getConfig(final Class<T> expectedClass, final String prefix, final Path filePath) {
        final ConfigSource configSource;
        try {
            configSource = resolveConfigSource(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read configuration file: " + filePath, e);
        }

        final var config = new SmallRyeConfigBuilder()
            .withSources(configSource)
            .withMapping(expectedClass, prefix)
            .addDefaultInterceptors()
            .build();

        return config.getConfigMapping(expectedClass, prefix);
    }

    private ConfigSource resolveConfigSource(final Path filePath) throws IOException {
        final URL url;
        if (Files.exists(filePath)) {
            url = filePath.toUri().toURL();
        } else {
            // Normalize path separators for classpath loading (windows support)
            final var resourcePath = filePath.toString().replace('\\', '/');
            url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        }

        if (url == null) {
            throw new IOException("Configuration file not found on filesystem or classpath: " + filePath);
        }

        final var fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            return new YamlConfigSource(url);
        } else {
            final var props = new Properties();
            try (var is = url.openStream()) {
                props.load(is);
            }
            return new PropertiesConfigSource(props, filePath.toString());
        }
    }
}
