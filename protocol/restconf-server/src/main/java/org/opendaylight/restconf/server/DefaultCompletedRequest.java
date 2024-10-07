/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link PreparedRequest} which is already complete.
 *
 * @param status response status
 * @param headers additional response headers
 * @param content response body content
 */
// TODO: body + no content-type should be disallowed: reconsider the design of this class
@Beta
@NonNullByDefault
public record DefaultCompletedRequest(HttpResponseStatus status, @Nullable HttpHeaders headers,
        @Nullable ByteBuf content)
        implements CompletedRequest {
    public DefaultCompletedRequest {
        requireNonNull(status);
    }

    public DefaultCompletedRequest(final HttpResponseStatus status) {
        this(status, null, null);
    }

    public DefaultCompletedRequest(final HttpResponseStatus status, final HttpHeaders headers) {
        this(status, requireNonNull(headers), null);
    }

    @Override
    public FullHttpResponse toHttpResponse(final HttpVersion version) {
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
