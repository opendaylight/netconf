/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link CompletedRequest} which has no body.
 */
public final class EmptyRequestResponse extends AbstractRequestResponse implements ReadyResponse {
    public EmptyRequestResponse(final @NonNull HttpResponseStatus status, final @Nullable HttpHeaders headers) {
        super(status, headers);
    }

    public EmptyRequestResponse(final @NonNull HttpResponseStatus status) {
        this(status, null);
    }

    @Override
    public FullHttpResponse toHttpResponse(final ByteBufAllocator alloc, final HttpVersion version) {
        return toHttpResponse(version, status, headers);
    }
}
