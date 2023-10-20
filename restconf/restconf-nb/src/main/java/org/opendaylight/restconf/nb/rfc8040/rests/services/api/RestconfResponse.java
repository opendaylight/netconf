/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The result of a RESTCONF request. It contains two things: a {@link #statusCode()} and a {@link #body()}
 *
 * @param statusCode HTTP status code
 * @param body Response body
 */
@NonNullByDefault
public record RestconfResponse(int statusCode, RestconfResponse.Body body) {
    /**
     * The body of a result.
     */
    interface Body {

    }

    public RestconfResponse {
        requireNonNull(body);
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("Invalid status code " + statusCode);
        }
    }
}
