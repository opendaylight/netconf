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
import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link RequestResponse} containing a {@link ByteBuf} body {@link #content()}, potentially with some
 * {@link #contentType()}.
 */
@NonNullByDefault
public record ByteBufResponse(HttpResponseStatus status, ByteBuf content, @Nullable AsciiString contentType)
        implements Response {
    public ByteBufResponse {
        requireNonNull(status);
        requireNonNull(content);
    }

    public ByteBufResponse(final HttpResponseStatus status, final ByteBuf content) {
        this(status, content, null);
    }

    public static ByteBufResponse ok(final ByteBuf content) {
        return new ByteBufResponse(HttpResponseStatus.OK, content, null);
    }

    public static ByteBufResponse ok(final ByteBuf content, final AsciiString contentType) {
        return new ByteBufResponse(HttpResponseStatus.OK, content, requireNonNull(contentType));
    }

    public FullHttpResponse format(final HttpVersion version) {
        final var message = new DefaultFullHttpResponse(version, status, content);
        final var headers = message.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        if (contentType != null) {
            headers.set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        return message;
    }
}
