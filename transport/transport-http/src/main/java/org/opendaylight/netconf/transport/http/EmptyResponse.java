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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link Response} which has no body.
 */
@NonNullByDefault
public record EmptyResponse(HttpResponseStatus status) implements Response {
    public static final EmptyResponse NO_CONTENT = new EmptyResponse(HttpResponseStatus.NO_CONTENT);
    public static final EmptyResponse NOT_FOUND = new EmptyResponse(HttpResponseStatus.NOT_FOUND);
    public static final EmptyResponse OK = new EmptyResponse(HttpResponseStatus.OK);

    public EmptyResponse {
        requireNonNull(status);
    }

    public FullHttpResponse format(final HttpVersion version) {
        return new DefaultFullHttpResponse(version, status, Unpooled.EMPTY_BUFFER);
    }
}
