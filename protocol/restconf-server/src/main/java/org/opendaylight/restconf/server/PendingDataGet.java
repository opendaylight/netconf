/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaders;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataGetResult;

/**
 * A GET or HEAD request to the /data resource.
 */
@NonNullByDefault
final class PendingDataGet extends AbstractPendingGet<DataGetResult> {
    private final ApiPath apiPath;

    PendingDataGet(final EndpointInvariants invariants, final MessageEncoding encoding, final ApiPath apiPath,
            final boolean withContent) {
        super(invariants, encoding, withContent);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataGetResult> request, final InputStream body) {
        final var server = server();
        if (apiPath.isEmpty()) {
            server.dataGET(request);
        } else {
            server.dataGET(request, apiPath);
        }
    }

    @Override
    Response transformResultImpl(final NettyServerRequest<?> request, final DataGetResult result) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
//      return responseBuilder(requestParams, HttpResponseStatus.OK)
//      .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
//      .setMetadataHeaders(result)
//      .setBody(result.body())
//      .build();
    }

    @Override
    void fillHeaders(final DataGetResult result, final HttpHeaders headers) {
//      .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
//      .setMetadataHeaders(result)
//      content-type
    }
}
