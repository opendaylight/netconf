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
import java.net.URI;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.server.api.OptionsResult;

/**
 * Abstract base class for {@link PendingRequest}s which result in an {@link OptionsResult}. These are mapped to a
 * response which contains a {@code Allow} header and perhaps an {@code Accept-Patch} header.
 */
@NonNullByDefault
abstract class AbstractPendingOptions extends AbstractPendingRequest<OptionsResult> {
    static final HttpHeaders HEADERS_ACTION = headers("OPTIONS, POST");
    static final HttpHeaders HEADERS_DATASTORE = patchHeaders("GET, HEAD, OPTIONS, PATCH, POST, PUT");
    static final HttpHeaders HEADERS_READ_ONLY = headers("GET, HEAD, OPTIONS");
    static final HttpHeaders HEADERS_RESOURCE = patchHeaders("DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT");
    static final HttpHeaders HEADERS_RPC = headers("GET, HEAD, OPTIONS, POST");

    static final CompletedRequest ACTION = new DefaultCompletedRequest(HttpResponseStatus.OK, HEADERS_ACTION);
    static final CompletedRequest DATASTORE = new DefaultCompletedRequest(HttpResponseStatus.OK, HEADERS_DATASTORE);
    static final CompletedRequest READ_ONLY = new DefaultCompletedRequest(HttpResponseStatus.OK, HEADERS_READ_ONLY);
    static final CompletedRequest RESOURCE = new DefaultCompletedRequest(HttpResponseStatus.OK, HEADERS_RESOURCE);
    static final CompletedRequest RPC = new DefaultCompletedRequest(HttpResponseStatus.OK, HEADERS_RPC);

    AbstractPendingOptions(final EndpointInvariants invariants, final URI targetUri) {
        super(invariants, targetUri);
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

    private static HttpHeaders headers(final String allow) {
        return DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders().set(HttpHeaderNames.ALLOW, allow);
    }

    private static HttpHeaders patchHeaders(final String allow) {
        return headers(allow).set(HttpHeaderNames.ACCEPT_PATCH,
            List.of(MediaTypes.APPLICATION_YANG_PATCH_JSON, MediaTypes.APPLICATION_YANG_PATCH_XML));
    }
}
