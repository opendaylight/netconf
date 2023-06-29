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
     * Get path parameters name for {@code path}.
     *
     * @return {@link List} of parameters
     */
    public static List<String> getDataPathParameters(final Map<String, Path> paths, final String path) {
        final var params = new ArrayList<String>();
        paths.get(path).put().parameters().elements()
            .forEachRemaining(p -> params.add(p.get("name").asText()));
        return params;
    }

    public static List<String> getOperationsPathParameters(final Map<String, Path> paths, final String path) {
        final var params = new ArrayList<String>();
        paths.get(path).post().parameters().elements()
            .forEachRemaining(p -> params.add(p.get("name").asText()));
        return params;
    }
}
