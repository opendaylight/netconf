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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.AsciiString;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link ReadyResponse} which has headers and no body.
 */
@NonNullByDefault
public record HeadersResponse(HttpResponseStatus status, ReadOnlyHttpHeaders headers) implements ReadyResponse {
    public HeadersResponse {
        requireNonNull(status);
        requireNonNull(headers);
    }

    private static HeadersResponse of(final boolean validateHeaders, final HttpResponseStatus status,
            final CharSequence... nameValuePairs) {
        if (nameValuePairs.length < 2) {
            throw new IllegalArgumentException("Require at least one pair, not " + Arrays.asList(nameValuePairs));
        }
        return ofNonEmpty(validateHeaders, status, nameValuePairs);
    }

    private static HeadersResponse ofNonEmpty(final boolean validateHeaders, final HttpResponseStatus status,
            final CharSequence... nameValuePairs) {
        for (var seq : nameValuePairs) {
            requireNonNull(seq);
        }
        return new HeadersResponse(status, new ReadOnlyHttpHeaders(validateHeaders, nameValuePairs));
    }

    public static HeadersResponse ofTrusted(final HttpResponseStatus status,
            final AsciiString name, final CharSequence value) {
        return ofNonEmpty(false, status, name, value);
    }

    public static HeadersResponse ofTrusted(final HttpResponseStatus status,
            final AsciiString name0, final CharSequence value0,
            final AsciiString name1, final CharSequence value1) {
        return ofNonEmpty(false, status, name0, value0, name1, value1);
    }

    public static HeadersResponse ofTrusted(final HttpResponseStatus status, final List<CharSequence> nameValuePairs) {
        return of(false, status, nameValuePairs.toArray(CharSequence[]::new));
    }

    public static HeadersResponse ofUntrusted(final HttpResponseStatus status,
            final AsciiString name, final CharSequence value) {
        return ofNonEmpty(true, status, name, value);
    }

    public static HeadersResponse ofUntrusted(final HttpResponseStatus status,
            final AsciiString name0, final CharSequence value0,
            final AsciiString name1, final CharSequence value1) {
        return ofNonEmpty(true, status, name0, value0, name1, value1);
    }

    public static HeadersResponse ofUntrusted(final HttpResponseStatus status,
            final List<CharSequence> nameValuePairs) {
        return of(true, status, nameValuePairs.toArray(CharSequence[]::new));
    }

    @Override
    public FullHttpResponse toHttpResponse(final HttpVersion version) {
        final var message = new DefaultFullHttpResponse(version, status, Unpooled.EMPTY_BUFFER);
        message.headers().set(headers).setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        return message;
    }
}
