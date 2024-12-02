/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A ready {@link Response} that can be immediately sent out.
 */
@NonNullByDefault
public non-sealed interface ReadyResponse extends Response {
    /**
     * Format this response to a {@link FullHttpResponse}.
     *
     * @param version {@link HttpVersion} of the response
     * @return a {@link FullHttpResponse}
     */
    FullHttpResponse toHttpResponse(HttpVersion version);
}
