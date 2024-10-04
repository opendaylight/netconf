/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpMethod;
import org.eclipse.jdt.annotation.NonNull;

/**
 * An enumeration of HTTP methods we implement. This does not include {@code CONNECT} and {@code TRACE} on purpose.
 *
 * <p>
 * This enumeration is not strictly necessary: we could operate on {@link HttpMethod} comparison and switch on strings
 * provided by {@link HttpMethod#name()}. On the other hand, the logic in {@link HTTPServerSession} checks whether a
 * particular {@link HttpMethod} is implemented -- which requires comparisons. This enumeration allows us to capture
 * the match of that check, so that we can later use cheaper dispatches.
 */
public enum ImplementedMethod {
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#name-delete">HTTP DELETE method</a>.
     */
    DELETE(HttpMethod.DELETE),
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#name-get">HTTP GET method</a>.
     */
    GET(HttpMethod.GET),
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#name-head">HTTP HEAD method</a>.
     */
    HEAD(HttpMethod.HEAD),
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#name-options">HTTP OPTIONS method</a>.
     */
    OPTIONS(HttpMethod.OPTIONS),
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc5789#section-2">HTTP PATCH method</a>.
     */
    PATCH(HttpMethod.PATCH),
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#name-post">HTTP POST method</a>.
     */
    POST(HttpMethod.POST),
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc9110#name-put">HTTP PUT method</a>.
     */
    PUT(HttpMethod.PUT);

    private final @NonNull HttpMethod httpMethod;

    ImplementedMethod(final HttpMethod httpMethod) {
        this.httpMethod = requireNonNull(httpMethod);
    }

    /**
     * Return the corresponding {@link HttpMethod}.
     *
     * @return the corresponding {@link HttpMethod}
     */
    public @NonNull HttpMethod httpMethod() {
        return httpMethod;
    }

    @Override
    public String toString() {
        return httpMethod.name();
    }
}
