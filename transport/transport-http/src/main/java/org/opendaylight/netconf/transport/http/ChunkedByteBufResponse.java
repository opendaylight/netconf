/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

// TODO
@NonNullByDefault
public record ChunkedByteBufResponse(HttpResponseStatus status, ByteBuf content, ReadOnlyHttpHeaders headers)
        implements ReadyResponse {
    private static final ReadOnlyHttpHeaders EMPTY_HEADERS = new ReadOnlyHttpHeaders(false);

    public ChunkedByteBufResponse {
        requireNonNull(status);
        requireNonNull(content);
        requireNonNull(headers);
    }

    public ChunkedByteBufResponse(final HttpResponseStatus status, final ByteBuf content) {
        this(status, content, EMPTY_HEADERS);
    }

    public ChunkedByteBufResponse(final HttpResponseStatus status, final ByteBuf content,
            final @Nullable AsciiString contentType) {
        // send chunked header?
        this(status, content, contentType == null ? EMPTY_HEADERS
            : new ReadOnlyHttpHeaders(false, HttpHeaderNames.CONTENT_TYPE, contentType,
                HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED));
    }

    public static ChunkedByteBufResponse ok(final ByteBuf content) {
        return new ChunkedByteBufResponse(HttpResponseStatus.OK, content);
    }

    public static ChunkedByteBufResponse ok(final ByteBuf content, final AsciiString contentType) {
        return new ChunkedByteBufResponse(HttpResponseStatus.OK, content, requireNonNull(contentType));
    }

    @Override
    public FullHttpResponse toHttpResponse(final HttpVersion version) {
        // send chunked header?
        final var message = new DefaultFullHttpResponse(version, status, content);
        message.headers().set(headers).setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        return message;
    }
}
