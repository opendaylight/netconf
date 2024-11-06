/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link RequestResponse} which has a {@link #cause()}.
 */
@NonNullByDefault
public final class ExceptionRequestResponse extends AbstractRequestResponse {
    private final Throwable cause;

    public ExceptionRequestResponse(final HttpResponseStatus status, final Throwable cause,
            final @Nullable HttpHeaders headers) {
        super(HttpResponseStatus.INTERNAL_SERVER_ERROR, headers);
        this.cause = requireNonNull(cause);
    }

    public ExceptionRequestResponse(final HttpResponseStatus status, final Throwable cause) {
        this(HttpResponseStatus.INTERNAL_SERVER_ERROR, cause, null);
    }

    public ExceptionRequestResponse(final Throwable cause) {
        this(HttpResponseStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public Throwable cause() {
        return cause;
    }

    @Override
    public FullHttpResponse toHttpResponse(final ByteBufAllocator alloc, final HttpVersion version) {
        final var buf = ByteBufUtil.writeUtf8(alloc, cause.toString());
        final var ret = new DefaultFullHttpResponse(version, status, buf);
        HttpUtil.setContentLength(ret, buf.readableBytes());
        return ret;
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("cause", cause);
    }
}
