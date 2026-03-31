/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Shared access to bundled OpenAPI explorer resources.
 */
public final class OpenApiStaticResources {
    private OpenApiStaticResources() {
        // Hidden on purpose
    }

    public static @Nullable URL getResource(final String path) {
        return OpenApiStaticResources.class.getResource("/explorer" + normalizePath(path));
    }

    private static String normalizePath(final String path) {
        final var normalized = requireNonNull(path);
        if (normalized.isEmpty() || "/".equals(normalized)) {
            return "/index.html";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
