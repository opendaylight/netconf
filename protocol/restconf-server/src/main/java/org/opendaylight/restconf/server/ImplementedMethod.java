/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpMethod;
import org.eclipse.jdt.annotation.NonNull;

/**
 * An enumeration of HTTP methods we implement. This does not include {@code CONNECT} and {@code TRACE} on purpose.
 *
 * <p>
 * This enumeration is not strictly necessary: we could operate on {@link HttpMethod} comparison and switch on strings
 * provided by {@link HttpMethod#name()}. On the other hand, the logic in {@link RestconfSession} checks whether a
 * particular {@link HttpMethod} is implemented -- which requires comparisons. This enumeration allows us to capture
 * the match of that check, so that we can later use cheaper dispatches.
 */
enum ImplementedMethod {
    DELETE(HttpMethod.DELETE),
    GET(HttpMethod.GET),
    HEAD(HttpMethod.HEAD),
    OPTIONS(HttpMethod.OPTIONS),
    PATCH(HttpMethod.PATCH),
    POST(HttpMethod.POST),
    PUT(HttpMethod.PUT);

    private final @NonNull HttpMethod httpMethod;

    ImplementedMethod(final HttpMethod httpMethod) {
        this.httpMethod = requireNonNull(httpMethod);
    }

    @NonNull HttpMethod httpMethod() {
        return httpMethod;
    }

    @Override
    public String toString() {
        return httpMethod.name();
    }
}
