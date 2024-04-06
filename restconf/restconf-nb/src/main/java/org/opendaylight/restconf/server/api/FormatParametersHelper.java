/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.util.Map;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * Helper utilities for structures which contain plain {@link FormatParameters}.
 */
final class FormatParametersHelper {
    private FormatParametersHelper() {
        // Hidden on purpose
    }

    static <T extends FormatParameters> @NonNull T ofQueryParameters(final Map<String, String> queryParameters,
            final Function<@NonNull PrettyPrintParam, @NonNull T> factory, final @NonNull T empty) {
        if (queryParameters.isEmpty()) {
            return empty;
        }

        var prettyPrint = PrettyPrintParam.FALSE;
        for (var entry : queryParameters.entrySet()) {
            final var paramName = entry.getKey();

            prettyPrint = switch (paramName) {
                case PrettyPrintParam.uriName -> EventStreamGetParams.mandatoryParam(PrettyPrintParam::forUriValue,
                    paramName, entry.getValue());
                default -> throw new IllegalArgumentException("Invalid parameter: " + paramName);
            };
        }
        return factory.apply(prettyPrint);
    }
}
