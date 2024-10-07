/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.server.CompletedRequest;
import org.opendaylight.restconf.server.DefaultCompletedRequest;
import org.opendaylight.restconf.server.NettyMediaTypes;
import org.opendaylight.restconf.server.PendingRequest;
import org.opendaylight.restconf.server.api.OptionsResult;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * Abstract base class for {@link PendingRequest}s which result in an {@link OptionsResult}. These are mapped to a
 * response which contains a {@code Allow} header and perhaps an {@code Accept-Patch} header.
 */
@NonNullByDefault
public abstract class AbstractPendingOptions extends PendingRequestWithApiPath<OptionsResult> {
    /**
     * The set of media types we accept for the PATCH method, formatted as a comma-separated single string.
     */
    static final String ACCEPTED_PATCH_MEDIA_TYPES = String.join(", ", List.of(
        // Plain patch
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        // YANG patch
        MediaTypes.APPLICATION_YANG_PATCH_JSON,
        MediaTypes.APPLICATION_YANG_PATCH_XML,
        // Legacy plain patch data
        // FIXME: do not advertize these types, because https://www.rfc-editor.org/errata/eid3169 specifically calls
        //        this out as NOT being the right thing
        HttpHeaderValues.APPLICATION_JSON.toString(),
        HttpHeaderValues.APPLICATION_XML.toString(),
        NettyMediaTypes.TEXT_XML.toString()));

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

    protected AbstractPendingOptions(final EndpointInvariants invariants, final TransportSession session,
            final URI targetUri, final @Nullable Principal principal, final ApiPath apiPath) {
        super(invariants, session, targetUri, principal, apiPath);
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

    private static HttpHeaders headers(final String allowValue) {
        return HEADERS_FACTORY.newEmptyHeaders().set(HttpHeaderNames.ALLOW, allowValue);
    }

    private static HttpHeaders patchHeaders(final String allowValue) {
        return headers(allowValue).set(HttpHeaderNames.ACCEPT_PATCH, ACCEPTED_PATCH_MEDIA_TYPES);
    }
}
