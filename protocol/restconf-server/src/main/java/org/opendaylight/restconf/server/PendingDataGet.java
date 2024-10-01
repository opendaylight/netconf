/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.RestconfServer;

/**
 * A GET or HEAD request to the /data resource.
 */
@NonNullByDefault
final class PendingDataGet extends PendingRequest {
    private final MessageEncoding encoding;
    private final ApiPath apiPath;
    private final boolean withContent;

    PendingDataGet(final MessageEncoding encoding, final ApiPath apiPath, final boolean withContent) {
        this.encoding = requireNonNull(encoding);
        this.apiPath = requireNonNull(apiPath);
        this.withContent = withContent;
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {
//        final var request = new NettyServerRequest<DataGetResult>(params, callback) {
//            @Override
//            FullHttpResponse transform(final DataGetResult result) {
//                return responseBuilder(requestParams, HttpResponseStatus.OK)
//                    .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
//                    .setMetadataHeaders(result)
//                    .setBody(result.body())
//                    .build();
//            }
//        };
//
//        if (apiPath.isEmpty()) {
//            server.dataGET(request);
//        } else {
//            server.dataGET(request, apiPath);
//        }
    }
}
