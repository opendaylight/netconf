/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.Beta;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A response to request. It can be turned into a response via {@link #toHttpResponse(HttpVersion)}.
 */
@Beta
@NonNullByDefault
public interface Response {
    /**
     * Return a {@link FullHttpResponse} representation of this object.
     *
     * @param alloc {@link ByteBufAllocator} to use for ByteBuf allocation
     * @param version HTTP version to use
     * @return a {@link FullHttpResponse}
     * @throws IOException when an I/O error occurs
     */
    FullHttpResponse toHttpResponse(ByteBufAllocator alloc, HttpVersion version) throws IOException;

    /**
     * Return a {@link FullHttpResponse} representation of this object.
     *
     * @param version HTTP version to use
     * @return a {@link FullHttpResponse}
     * @throws IOException when an I/O error occurs
     */
    default FullHttpResponse toHttpResponse(final HttpVersion version) throws IOException {
        return toHttpResponse(UnpooledByteBufAllocator.DEFAULT, version);
    }
}
