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
 * A PUT request to the /data resource.
 */
@NonNullByDefault
final class PendingDataPut extends PendingRequest {
    private final MessageEncoding encoding;
    private final ApiPath apiPath;

    PendingDataPut(final MessageEncoding encoding, final ApiPath apiPath) {
        this.encoding = requireNonNull(encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {
//        final var request = new NettyServerRequest<DataPutResult>(params, callback) {
//            @Override
//            FullHttpResponse transform(final DataPutResult result) {
//                final var status = result.created() ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT;
//                return responseBuilder(requestParams, status).setMetadataHeaders(result).build();
//            }
//        };
//        final var dataResourceBody = requestBody(params, JsonResourceBody::new, XmlResourceBody::new);
//        if (apiPath.isEmpty()) {
//            server.dataPUT(request, dataResourceBody);
//        } else {
//            server.dataPUT(request, apiPath, dataResourceBody);
//        }
    }
}
