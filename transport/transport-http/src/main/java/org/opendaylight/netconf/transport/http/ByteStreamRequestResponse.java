/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link AbstractRequestResponse} additionally holding a body representation which can be turned into a byte
 * stream.
 */
@NonNullByDefault
public abstract class ByteStreamRequestResponse extends AbstractRequestResponse {
    protected ByteStreamRequestResponse(final HttpResponseStatus status, final @Nullable HttpHeaders headers) {
        super(status, headers);
    }

    protected ByteStreamRequestResponse(final ByteBufRequestResponse prev) {
        super(prev);
    }

    @Override
    public final FullHttpResponse toHttpResponse(final ByteBufAllocator alloc, final HttpVersion version)
            throws IOException {
        final var content = alloc.buffer();
        try (var out = new ByteBufOutputStream(content)) {
            writeBody(out);
        } catch (IOException e) {
            content.release();
            throw e;
        }
        return toHttpResponse(version, content);
    }

    protected abstract FullHttpResponse toHttpResponse(HttpVersion version, ByteBuf content);

    protected static final FullHttpResponse toHttpResponse(final HttpVersion version, final HttpResponseStatus status,
            final @Nullable HttpHeaders headers, final ByteBuf content, final AsciiString contentType) {
        final var response = toHttpResponse(version, status, headers, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        return response;
    }

    protected abstract void writeBody(OutputStream out) throws IOException;
}
