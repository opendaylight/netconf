/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;

/**
 * A marker interface for {@link Response}s which are readily available.
 */
public interface ReadyResponse extends Response {
    @Override
    FullHttpResponse toHttpResponse(ByteBufAllocator alloc, HttpVersion version) ;

    @Override
    default FullHttpResponse toHttpResponse(final HttpVersion version) {
        return toHttpResponse(UnpooledByteBufAllocator.DEFAULT, version);
    }
}
