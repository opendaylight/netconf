/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.config;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePropertySource;

@Singleton
@NonNullByDefault
public final class SpringbootConfigLoader implements ConfigLoader {

    @Override
    public <T> T getConfig(final Class<T> expectedClass, final String prefix, final Path filePath) {
        final var resource = resolveResource(filePath);
        final var fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        final var sourceName = prefix + fileName;
        final List<PropertySource<?>> sources;
        // Load Property Sources based on file type.
        try {
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                sources = new YamlPropertySourceLoader().load(sourceName, resource);
            } else {
                sources = List.of(new ResourcePropertySource(sourceName, resource));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load configuration from " + fileName, ex);
        }

        // Add sources to Environment.
        final var environment = new StandardEnvironment();
        sources.forEach(environment.getPropertySources()::addFirst);

        return new Binder(ConfigurationPropertySources.from(environment.getPropertySources()))
            .bind(prefix, expectedClass)
            .orElseThrow(() -> new IllegalStateException(
                "Configuration property not found with prefix: " + prefix));
    }

    private static Resource resolveResource(final Path fileName) {
        if (Files.exists(fileName)) {
            return new FileSystemResource(fileName.toFile());
        }
        return new ClassPathResource(fileName.toString());
    }
}
