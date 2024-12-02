/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ReadyResponse;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * A PUT request to the /data resource.
 */
@NonNullByDefault
final class PendingDataPut extends PendingRequestWithResource<DataPutResult> {
    PendingDataPut(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding contentEncoding, final ApiPath apiPath) {
        super(invariants, session, targetUri, principal, contentEncoding, apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataPutResult> request, final ResourceBody body) {
        if (apiPath.isEmpty()) {
            server().dataPUT(request, body);
        } else {
            server().dataPUT(request, apiPath, body);
        }
    }

    @Override
    ReadyResponse transformResult(final NettyServerRequest<?> request, final DataPutResult result) {
        final var status = result.created() ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT;
        final var headers = metadataHeaders(result);
        return headers.isEmpty() ? new EmptyResponse(status) : HeadersResponse.of(status, headers);
    }
}
