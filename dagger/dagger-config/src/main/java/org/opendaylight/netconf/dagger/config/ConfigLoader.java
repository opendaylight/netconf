/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.config;

import java.nio.file.Path;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Interface responsible for loading external configuration for components.
 *
 * <p>This is primarily intended for components that are not initialized or managed by the OSGi.
 */
@NonNullByDefault
public interface ConfigLoader {

    /**
     * Loads configuration properties from an external file or classpath resource and maps them to a target class.
     *
     * @param expectedClass Class type to which the configuration properties should be mapped
     * @param prefix        Common prefix for the configuration properties (e.g., "network.server")
     * @param filePath      Path of the configuration file (e.g., "netconf.yaml" or "config/app.properties")
     * @param <T>           Type of the configuration class
     * @return an instance of {@code expectedClass} populated with the loaded configuration properties
     * @throws IllegalStateException if the configuration file cannot be found, read, or bound to the class
     */
    <T> T getConfig(Class<T> expectedClass, String prefix, Path filePath);
}
