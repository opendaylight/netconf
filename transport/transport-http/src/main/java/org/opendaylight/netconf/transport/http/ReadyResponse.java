/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.Beta;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A marker interface for {@link Response}s which are readily available. Examples include {@link EmptyRequestResponse},
 * {@link ByteBufRequestResponse}, and similar.
 */
@Beta
@NonNullByDefault
public interface ReadyResponse extends Response {

    default FullHttpResponse toHttpResponse(final HttpVersion version) {
        final var response = new DefaultFullHttpResponse(version, status());
        fillHeaders(response.headers());
        // FIXME: body?
        return response;
    }
}
