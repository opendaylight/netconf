/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * Access to RFC8040 streams.
 */
@NonNullByDefault
final class StreamsResource extends AbstractLeafResource {
    StreamsResource(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return switch (method) {
            case GET -> startStream(session, targetUri, headers, principal, path, true);

            // FIXME do we support HEAD and OPTIONS?
            // case HEAD -> prepareGet(session, targetUri, headers, principal, path, false);
            // case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private PreparedRequest startStream(final TransportSession session, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean b) {
        // FIXME start stream and return initial response
        return null;
    }
}
