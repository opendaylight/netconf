/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link ReadyResponse} containing a {@link ByteBuf} body {@link #content()}, potentially with some headers.
 */
@NonNullByDefault
public record ByteBufResponse(HttpResponseStatus status, ByteBuf content, ReadOnlyHttpHeaders headers)
        implements ReadyResponse {
    private static final ReadOnlyHttpHeaders EMPTY_HEADERS = new ReadOnlyHttpHeaders(false);

    public ByteBufResponse {
        requireNonNull(status);
        requireNonNull(content);
        requireNonNull(headers);
    }

    public ByteBufResponse(final HttpResponseStatus status, final ByteBuf content) {
        this(status, content, EMPTY_HEADERS);
    }

    public ByteBufResponse(final HttpResponseStatus status, final ByteBuf content,
            final @Nullable AsciiString contentType) {
        this(status, content, contentType == null ? EMPTY_HEADERS
            : new ReadOnlyHttpHeaders(false, HttpHeaderNames.CONTENT_TYPE, contentType));
    }

    public static ByteBufResponse ok(final ByteBuf content) {
        return new ByteBufResponse(HttpResponseStatus.OK, content);
    }

    public static ByteBufResponse ok(final ByteBuf content, final AsciiString contentType) {
        return new ByteBufResponse(HttpResponseStatus.OK, content, requireNonNull(contentType));
    }

    @Override
    public FullHttpResponse toHttpResponse(final HttpVersion version) {
        final var message = new DefaultFullHttpResponse(version, status, content);
        message.headers().set(headers).setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        return message;
    }
}
