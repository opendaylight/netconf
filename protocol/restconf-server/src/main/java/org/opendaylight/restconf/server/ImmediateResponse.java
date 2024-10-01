/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.OptionsResult;

/**
 * A {@link PreparedRequest} which is already complete.
 *
 * @param status response status
 * @param header response headers
 * @param body response body
 */
@NonNullByDefault
record ImmediateResponse(HttpResponseStatus status, @Nullable HttpHeaders headers, @Nullable ByteBuf content)
        implements PreparedRequest {
    private static final ImmediateResponse NOT_FOUND = new ImmediateResponse(HttpResponseStatus.NOT_FOUND);

    ImmediateResponse {
        requireNonNull(status);
    }

    ImmediateResponse(final HttpResponseStatus status) {
        this(status, null, null);
    }

    ImmediateResponse(final HttpResponseStatus status, final HttpHeaders headers) {
        this(status, requireNonNull(headers), null);
    }

    ImmediateResponse(final HttpResponseStatus status, final ByteBuf content) {
        this(status, null, requireNonNull(content));
    }

    static ImmediateResponse badRequest(final String path, final ParseException cause) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    static PreparedRequest notAcceptable(final String acceptValues) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    static ImmediateResponse notFound() {
        return NOT_FOUND;
    }

    static ImmediateResponse methodNotAllowed(final OptionsResult options) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    FullHttpResponse toHttpResponse(final HttpVersion version) {
        return toHttpResponse(version, status, headers, content);
    }

    // split out to make make null checks work against local variables
    private static FullHttpResponse toHttpResponse(final HttpVersion version, final HttpResponseStatus status,
            final @Nullable HttpHeaders headers, final @Nullable ByteBuf content) {
        final var response = new DefaultFullHttpResponse(version, status,
            content != null ? content : Unpooled.EMPTY_BUFFER);
        final var responseHeaders = response.headers();
        if (headers != null) {
            responseHeaders.set(headers);
        }
        if (content != null) {
            responseHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }
        return response;
    }

}
