/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.restconf.openapi.model.Parameter;
import org.opendaylight.restconf.openapi.model.Path;

public final class OpenApiTestUtils {

    private OpenApiTestUtils() {
        // Hidden on purpose
    }

    /**
     * Get path parameters names for {@code path} for GET operation.
     *
     * @return {@link Set} of parameters excluding `content` parameter
     */
    public static Set<String> getPathGetParameters(final Map<String, Path> paths, final String path) {
        return paths.get(path).get().parameters()
            .stream()
            .map(Parameter::name)
            .filter(p -> !"content".equals(p))
            .collect(Collectors.toSet());
    }

    /**
     * Get path parameters names for {@code path} for POST operation.
     *
     * @return {@link Set} of parameters
     */
    public static Set<String> getPathPostParameters(final Map<String, Path> paths, final String path) {
        return paths.get(path).post().parameters()
            .stream()
            .map(Parameter::name)
            .collect(Collectors.toSet());
    }
}
