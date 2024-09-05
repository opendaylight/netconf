/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

final class ResponseUtils {

    private ResponseUtils() {
        // hidden on purpose
    }

    static FullHttpResponse simpleResponse(final HttpVersion version, final HttpResponseStatus status) {
        return new DefaultFullHttpResponse(version, status, Unpooled.EMPTY_BUFFER);
    }

    static FullHttpResponse simpleResponse(final HttpVersion version, final HttpResponseStatus status,
            final CharSequence headerName, final String headerValue) {
        final var response = simpleResponse(version, status);
        response.headers().set(headerName, headerValue);
        return response;
    }

    static FullHttpResponse responseWithContent(final HttpVersion version, final ByteBuf content,
            final CharSequence mediaType) {
        final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK, content);
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, mediaType)
            .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    static FullHttpResponse notFoundResponse(final HttpVersion version) {
        return simpleResponse(version, HttpResponseStatus.NOT_FOUND);
    }

    static FullHttpResponse optionsResponse(final HttpVersion version, final String allowHeader) {
        return simpleResponse(version, HttpResponseStatus.OK, HttpHeaderNames.ALLOW, allowHeader);
    }

    static FullHttpResponse redirectResponse(final HttpVersion version, final String locationHeader) {
        return simpleResponse(version, HttpResponseStatus.SEE_OTHER, HttpHeaderNames.LOCATION, locationHeader);
    }
}
