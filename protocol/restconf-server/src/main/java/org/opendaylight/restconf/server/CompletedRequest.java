/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
record CompletedRequest(HttpResponseStatus status, @Nullable HttpHeaders headers, @Nullable ByteBuf content)
        implements PreparedRequest, Response {
    private static final CompletedRequest NO_CONTENT = new CompletedRequest(HttpResponseStatus.NO_CONTENT);
    private static final CompletedRequest NOT_FOUND = new CompletedRequest(HttpResponseStatus.NOT_FOUND);
    private static final Map<OptionsResult, CompletedRequest> METHOD_NOT_ALLOWED =
        Arrays.stream(OptionsResult.values()).collect(Collectors.toUnmodifiableMap(Function.identity(),
            options -> new CompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, headersOf(options))));

    CompletedRequest {
        requireNonNull(status);
    }

    CompletedRequest(final HttpResponseStatus status) {
        this(status, null, null);
    }

    CompletedRequest(final HttpResponseStatus status, final HttpHeaders headers) {
        this(status, requireNonNull(headers), null);
    }

    CompletedRequest(final HttpResponseStatus status, final ByteBuf content) {
        this(status, null, requireNonNull(content));
    }

    static CompletedRequest badRequest(final AsciiString headerName) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    static CompletedRequest badRequest(final String path, final ParseException cause) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    static CompletedRequest methodNotAllowed(final OptionsResult options) {
        return verifyNotNull(METHOD_NOT_ALLOWED.get(requireNonNull(options)));
    }

    static CompletedRequest noContent() {
        return NO_CONTENT;
    }

    static CompletedRequest notAcceptable(final String acceptValues) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    static CompletedRequest notFound() {
        return NOT_FOUND;
    }

    static CompletedRequest unsupportedMediaType(final String acceptValues) {
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

    private static HttpHeaders headersOf(final OptionsResult result) {
//        final var headers = DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders();
//        headers.set(HttpHeaderNames.ACCEPT,
//
//            "GET, HEAD, OPTIONS");
        return null;
    }

}
