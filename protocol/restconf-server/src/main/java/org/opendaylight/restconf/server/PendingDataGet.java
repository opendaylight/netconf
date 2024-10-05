/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * A GET or HEAD request to the /data resource.
 */
@NonNullByDefault
final class PendingDataGet extends AbstractPendingGet<DataGetResult> {
    private final ApiPath apiPath;

    PendingDataGet(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding encoding, final ApiPath apiPath,
            final boolean withContent) {
        super(invariants, session, targetUri, principal, encoding, withContent);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataGetResult> request) {
        if (apiPath.isEmpty()) {
            server().dataGET(request);
        } else {
            server().dataGET(request, apiPath);
        }
    }

    @Override
    Response transformResultImpl(final NettyServerRequest<?> request, final DataGetResult result) {
        return new FormattableDataResponse(
            metadataHeaders(result).set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE), result.body(),
            encoding, request.prettyPrint());
    }

    @Override
    void fillHeaders(final DataGetResult result, final HttpHeaders headers) {
        setMetadataHeaders(headers, result)
            .set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
            .set(HttpHeaderNames.CONTENT_TYPE, encoding.dataMediaType());
    }
}
