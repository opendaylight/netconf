/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static io.netty.handler.codec.http.DefaultHttpHeadersFactory.headersFactory;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.CompletedRequest;
import org.opendaylight.netconf.transport.http.EmptyRequestResponse;

/**
 * Various utility {@link CompletedRequests}. These notably do not include anything that is inherently tied to RESTCONF.
 */
@NonNullByDefault
final class CompletedRequests {
    /**
     * A {@code 405 Method Not Allowed} response listing {@code GET}, {@code HEAD} and {@code OPTIONS} as the only
     * allowed methods.
     */
    static final EmptyRequestResponse METHOD_NOT_ALLOWED_GET =
        new EmptyRequestResponse(HttpResponseStatus.METHOD_NOT_ALLOWED,
            headersFactory().newEmptyHeaders().set(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS"));
    /**
     * A {@code 405 Method Not Allowed} response listing {@code OPTIONS} as the only allowed method.
     */
    static final EmptyRequestResponse METHOD_NOT_ALLOWED_OPTIONS =
        new EmptyRequestResponse(HttpResponseStatus.METHOD_NOT_ALLOWED,
            headersFactory().newEmptyHeaders().set(HttpHeaderNames.ALLOW, "OPTIONS"));
    /**
     * A {@code 204 No Content} as a {@link CompletedRequest}.
     */
    static final EmptyRequestResponse NO_CONTENT = new EmptyRequestResponse(HttpResponseStatus.NO_CONTENT);
    /**
     * A {@code 404 Not Found} as a {@link CompletedRequest}.
     */
    static final EmptyRequestResponse NOT_FOUND = new EmptyRequestResponse(HttpResponseStatus.NOT_FOUND);
    /**
     * A {@code 200 OK} response listing {@code GET}, {@code HEAD} and {@code OPTIONS} as the only allowed methods.
     */
    static final EmptyRequestResponse OK_GET = new EmptyRequestResponse(HttpResponseStatus.OK,
        headersFactory().newEmptyHeaders().set(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS"));
    /**
     * A {@code 200 OK} response listing {@code OPTIONS} as the only allowed method.
     */
    static final EmptyRequestResponse OK_OPTIONS = new EmptyRequestResponse(HttpResponseStatus.OK,
        headersFactory().newEmptyHeaders().set(HttpHeaderNames.ALLOW, "OPTIONS"));

    private CompletedRequests() {
        // Hidden on purpose
    }
}
