/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.OptionsResult;

@NonNullByDefault
final class OptionsServerRequest extends NettyServerRequest<OptionsResult> {
    OptionsServerRequest(final RequestParameters requestParameters, final RestconfRequest callback) {
        super(requestParameters, callback);
    }

    @Override
    FullHttpResponse transform(final OptionsResult result) {
        return switch (result) {
            case ACTION -> withoutPatch("OPTIONS, POST");
            case DATASTORE -> withPatch("GET, HEAD, OPTIONS, PATCH, POST, PUT");
            case RESOURCE -> withPatch("DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT");
            case READ_ONLY -> withoutPatch("GET, HEAD, OPTIONS");
            case RPC -> withoutPatch("GET, HEAD, OPTIONS, POST");
        };
    }

    private FullHttpResponse withPatch(final String allow) {
        final var response = withoutPatch(allow);
        response.headers().add(HttpHeaderNames.ACCEPT_PATCH, NettyMediaTypes.ACCEPT_PATCH_HEADER_VALUE);
        return response;
    }

    private FullHttpResponse withoutPatch(final String allow) {
        return withoutPatch(requestParams.protocolVersion(), allow);
    }

    static FullHttpResponse withoutPatch(final HttpVersion version, final String allow) {
        final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.ALLOW, allow);
        return response;
    }
}
