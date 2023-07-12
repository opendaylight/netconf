/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import java.util.List;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Parameter;

public final class OpenApiTestUtils {

    private OpenApiTestUtils() {
        // Hidden on purpose
    }

    /**
     * Get path parameters name for {@code operation}.
     *
     * @return {@link List} of parameters
     */
    public static List<String> getPathParameters(final Operation operation) {
        return operation.parameters()
            .stream()
            .map(Parameter::name)
            .toList();
    }
}
