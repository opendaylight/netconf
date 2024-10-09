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
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link RequestResponse} containing a {@link CharSource} of a specified media type.
 */
@Beta
@NonNullByDefault
public final class ByteSourceResponse extends ByteStreamRequestResponse {
    private final ByteSource source;
    private final AsciiString contentType;

    public ByteSourceResponse(final ByteSource source, final AsciiString contentType) {
        super(HttpResponseStatus.OK, null);
        this.source = requireNonNull(source);
        this.contentType = requireNonNull(contentType);
    }

    @Override
    protected void writeBody(final OutputStream out) throws IOException {
        source.copyTo(out);
    }

    @Override
    protected FullHttpResponse toHttpResponse(final HttpVersion version, final ByteBuf content) {
        return toHttpResponse(version, status, headers, content, contentType);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("contentType", contentType).add("source", source);
    }
}
