/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.OptionsResult;

/**
 * Abstract base class for {@link PendingRequest}s which result in an {@link OptionsResult}. These are mapped to a
 * response which contains a {@code Allow} header and perhaps an {@code Accept-Patch} header.
 */
@NonNullByDefault
abstract class AbstractPendingOptions extends PendingRequest<OptionsResult> {
    private static final CompletedRequest ACTION = withoutPatch("OPTIONS, POST");
    private static final CompletedRequest DATASTORE = withPatch("GET, HEAD, OPTIONS, PATCH, POST, PUT");
    private static final CompletedRequest RESOURCE = withPatch("DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT");
    private static final CompletedRequest RPC = withoutPatch("GET, HEAD, OPTIONS, POST");

    // Exposed due to its usefulness
    static final CompletedRequest READ_ONLY = withoutPatch("GET, HEAD, OPTIONS");

    AbstractPendingOptions(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    final CompletedRequest transformResult(final NettyServerRequest<?> request, final OptionsResult result) {
        return switch (result) {
            case ACTION -> ACTION;
            case DATASTORE -> DATASTORE;
            case READ_ONLY -> READ_ONLY;
            case RESOURCE -> RESOURCE;
            case RPC -> RPC;
        };
    }

    private static CompletedRequest withPatch(final String allow) {
        return new CompletedRequest(HttpResponseStatus.OK,
            headers(allow).add(HttpHeaderNames.ACCEPT_PATCH, NettyMediaTypes.ACCEPT_PATCH_HEADER_VALUE));
    }

    private static CompletedRequest withoutPatch(final String allow) {
        return new CompletedRequest(HttpResponseStatus.OK, headers(allow));
    }

    private static HttpHeaders headers(final String allow) {
        return DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders().set(HttpHeaderNames.ACCEPT, allow);
    }
}
