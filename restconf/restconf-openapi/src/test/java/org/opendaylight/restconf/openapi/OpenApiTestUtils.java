/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.openapi.model.Path;

public final class OpenApiTestUtils {

    private OpenApiTestUtils() {
        // Hidden on purpose
    }

    /**
     * Get path parameters names for {@code path} for GET operation.
     *
     * @return {@link List} of parameters excluding `content` parameter
     */
    public static List<String> getPathGetParameters(final Map<String, Path> paths, final String path) {
        final var params = new ArrayList<String>();
        paths.get(path).get().parameters().elements()
            .forEachRemaining(p -> {
                final String name = p.get("name").asText();
                if (!"content".equals(name)) {
                    params.add(name);
                }
            });
        return params;
    }

    /**
     * Get path parameters names for {@code path} for POST operation.
     *
     * @return {@link List} of parameters
     */
    public static List<String> getPathPostParameters(final Map<String, Path> paths, final String path) {
        final var params = new ArrayList<String>();
        paths.get(path).post().parameters().elements()
            .forEachRemaining(p -> params.add(p.get("name").asText()));
        return params;
    }
}
