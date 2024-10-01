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
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.ResourceBody;

/**
 * A PATCH request to the /data resource with YANG Data payload.
 */
@NonNullByDefault
final class PendingDataPatchPlain extends PendingRequestWithResource<DataPatchResult> {
    PendingDataPatchPlain(final EndpointInvariants invariants, final URI targetUri, final @Nullable Principal principal,
            final MessageEncoding contentEncoding, final ApiPath apiPath) {
        super(invariants, targetUri, principal, contentEncoding, apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataPatchResult> request, final ResourceBody body) {
        if (apiPath.isEmpty()) {
            server().dataPATCH(request, body);
        } else {
            server().dataPATCH(request, apiPath, body);
        }
    }

    @Override
    Response transformResult(final NettyServerRequest<?> request, final DataPatchResult result) {
        return new DefaultCompletedRequest(HttpResponseStatus.OK, metadataHeaders(result));
    }
}
