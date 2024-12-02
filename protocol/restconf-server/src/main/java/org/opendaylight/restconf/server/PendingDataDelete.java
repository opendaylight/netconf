/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A DELETE request to the /data resource.
 */
@NonNullByDefault
final class PendingDataDelete extends PendingRequestWithApiPath<Empty> {
    PendingDataDelete(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final ApiPath apiPath) {
        super(invariants, session, targetUri, principal, apiPath);
    }

    @Override
    void execute(final NettyServerRequest<Empty> request) {
        server().dataDELETE(request, apiPath);
    }

    @Override
    EmptyResponse transformResult(final NettyServerRequest<?> request, final Empty result) {
        return EmptyResponse.NO_CONTENT;
    }
}
