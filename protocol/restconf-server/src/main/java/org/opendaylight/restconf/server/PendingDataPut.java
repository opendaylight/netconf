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
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.ResourceBody;

/**
 * A PUT request to the /data resource.
 */
@NonNullByDefault
final class PendingDataPut extends PendingRequestWithResourceBody<DataPutResult> {
    private final ApiPath apiPath;

    PendingDataPut(final EndpointInvariants invariants, final MessageEncoding encoding, final ApiPath apiPath) {
        super(invariants, encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataPutResult> request, final ResourceBody body) {
        final var server = server();
        if (apiPath.isEmpty()) {
            server.dataPUT(request, body);
        } else {
            server.dataPUT(request, apiPath, body);
        }
    }

    @Override
    Response transformResult(final NettyServerRequest<?> request, final DataPutResult result) {
//      final var status = result.created() ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT;
//      return responseBuilder(requestParams, status).setMetadataHeaders(result).build();
        throw new UnsupportedOperationException();
    }
}
