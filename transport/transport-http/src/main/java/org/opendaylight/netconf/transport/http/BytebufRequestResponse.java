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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link RequestResponse} containing a {@link ByteBuf} body.
 */
@Beta
@NonNullByDefault
public final class BytebufRequestResponse extends AbstractRequestResponse implements ByteBufHolder, ReadyResponse {
    private final ByteBuf content;

    private BytebufRequestResponse(final HttpResponseStatus status, final @Nullable HttpHeaders headers,
            final ByteBuf content) {
        super(status, headers);
        this.content = requireNonNull(content);
    }

    private BytebufRequestResponse(final BytebufRequestResponse prev, final ByteBuf content) {
        super(prev);
        this.content = requireNonNull(content);
    }

    public BytebufRequestResponse(final HttpResponseStatus status, final ByteBuf content, final HttpHeaders headers) {
        this(status, requireNonNull(headers), content);
    }

    // TODO: we really should require "content-type" at the very least. reconsider the design of this class?
    public BytebufRequestResponse(final HttpResponseStatus status, final ByteBuf content) {
        this(status, null, content);
    }

    @Override
    public FullHttpResponse toHttpResponse(final HttpVersion version) {
        return toHttpResponse(version, status, headers, content.retainedSlice());
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("size", content.readableBytes());
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public BytebufRequestResponse copy() {
        return replace(content.copy());
    }

    @Override
    public BytebufRequestResponse duplicate() {
        return replace(content.duplicate());
    }

    @Override
    public BytebufRequestResponse retainedDuplicate() {
        return replace(content.retainedDuplicate());
    }

    @Override
    public BytebufRequestResponse replace(final @Nullable ByteBuf newContent) {
        return new BytebufRequestResponse(this, requireNonNull(newContent));
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public BytebufRequestResponse retain() {
        content.retain();
        return this;
    }

    @Override
    public BytebufRequestResponse retain(final int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public BytebufRequestResponse touch() {
        content.touch();
        return this;
    }

    @Override
    public BytebufRequestResponse touch(final @Nullable Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(final int decrement) {
        return content.release(decrement);
    }
}
