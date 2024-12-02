/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link ReadyResponse} which has no body nor headers.
 *
 * @param status the response status
 */
@NonNullByDefault
public record EmptyResponse(HttpResponseStatus status) implements ReadyResponse {
    /**
     * An empty {@code 204 No Content} response.
     */
    public static final EmptyResponse NO_CONTENT = new EmptyResponse(HttpResponseStatus.NO_CONTENT);
    /**
     * An empty {@code 404 Not Found} response.
     */
    public static final EmptyResponse NOT_FOUND = new EmptyResponse(HttpResponseStatus.NOT_FOUND);
    /**
     * An empty {@code 200 OK} response.
     */
    public static final EmptyResponse OK = new EmptyResponse(HttpResponseStatus.OK);

    public EmptyResponse {
        requireNonNull(status);
    }

    @Override
    public FullHttpResponse toHttpResponse(final HttpVersion version) {
        return new DefaultFullHttpResponse(version, status, Unpooled.EMPTY_BUFFER);
    }
}
