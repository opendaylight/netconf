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
import javax.inject.Inject;
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
    private static final String DEFAULT_CONFIGURATION_FOLDER = "config";

    @Inject
    public SpringbootConfigLoader() {
    }

    @Override
    public <T> T getConfig(final Class<T> expectedClass, final String identifier, final String fileName) {
        final var resource = resolveResource(fileName);
        final var sourceName = identifier + fileName;
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
            .bind(identifier, expectedClass)
            .orElseThrow(() -> new IllegalStateException(
                "Configuration property not found with prefix: " + identifier));
    }

    private Resource resolveResource(final String fileName) {
        final var externalPath = Path.of(DEFAULT_CONFIGURATION_FOLDER, fileName);
        if (Files.exists(externalPath)) {
            return new FileSystemResource(externalPath.toFile());
        }
        return new ClassPathResource(fileName);
    }
}
