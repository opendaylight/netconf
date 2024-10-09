/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
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
 * An abstract baseline class promising co-implementation of {@link CompletedRequest} and {@link Response}.
 */
@Beta
@NonNullByDefault
public abstract class RequestResponse implements CompletedRequest, Response {
    @Override
    public final Response asResponse() {
        return this;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected abstract ToStringHelper addToStringAttributes(ToStringHelper helper);

    protected static final FullHttpResponse toHttpResponse(final HttpVersion version, final HttpResponseStatus status,
            final @Nullable HttpHeaders headers) {
        return toHttpResponseImpl(version, status, headers, Unpooled.EMPTY_BUFFER);
    }

    protected static final FullHttpResponse toHttpResponse(final HttpVersion version, final HttpResponseStatus status,
            final @Nullable HttpHeaders headers, final ByteBuf content) {
        return toHttpResponseImpl(version, status, headers, requireNonNull(content));
    }

    private static FullHttpResponse toHttpResponseImpl(final HttpVersion version, final HttpResponseStatus status,
            final @Nullable HttpHeaders headers, final ByteBuf content) {
        final var response = new DefaultFullHttpResponse(version, status, content);
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
