/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.ResourceBody;

/**
 * A PATCH request to the /data resource with YANG Data payload.
 */
@NonNullByDefault
final class PendingDataPatchPlain extends PendingRequestWithResourceBody<DataPatchResult> {
    private final ApiPath apiPath;

    PendingDataPatchPlain(final EndpointInvariants invariants, final URI targetUri, final MessageEncoding encoding,
            final ApiPath apiPath) {
        super(invariants, targetUri, encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataPatchResult> request, final ResourceBody body) {
        final var server = server();
        if (apiPath.isEmpty()) {
            server.dataPATCH(request, body);
        } else {
            server.dataPATCH(request, apiPath, body);
        }
    }

    @Override
    Response transformResult(final NettyServerRequest<?> request, final DataPatchResult result) {
        return new CompletedRequest(HttpResponseStatus.OK, metadataHeaders(result));
    }
}
