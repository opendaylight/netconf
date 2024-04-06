/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

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

    static <T extends FormatParameters> @NonNull T of(final QueryParams params, final @NonNull T compact,
            final @NonNull T pretty) {
        var prettyPrint = params.prettyPrint();
        for (var entry : params.asCollection()) {
            final var paramName = entry.getKey();

            prettyPrint = switch (paramName) {
                case PrettyPrintParam.uriName -> EventStreamGetParams.mandatoryParam(PrettyPrintParam::forUriValue,
                    paramName, entry.getValue());
                default -> throw new IllegalArgumentException("Invalid parameter: " + paramName);
            };
        }
        return prettyPrint.value() ? pretty : compact;
    }
}
