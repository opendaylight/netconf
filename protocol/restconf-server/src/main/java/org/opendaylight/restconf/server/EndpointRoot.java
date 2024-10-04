/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpRequest;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * The root of resource hierarchy exposed from a particular endpoint.
 */
@NonNullByDefault
final class EndpointRoot {
    private final PrincipalService principalService;
    private final WellKnownResources wellKnown;
    private final APIResource apiResource;

    EndpointRoot(final PrincipalService principalService, final WellKnownResources wellKnown,
            final APIResource apiResource) {
        this.principalService = requireNonNull(principalService);
        this.wellKnown = requireNonNull(wellKnown);
        this.apiResource = requireNonNull(apiResource);
    }

    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpRequest request) {
        final var peeler = new SegmentPeeler(targetUri);
        if (!peeler.hasNext()) {
            // We only support OPTIONS
            return method == ImplementedMethod.OPTIONS ? AbstractResource.OPTIONS_ONLY_OK
                : AbstractResource.OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        }

        final var segment = peeler.next();
        if (segment.equals(".well-known")) {
            return wellKnown.request(peeler, method);
        } else if (segment.equals(apiResource.firstSegment())) {
            return apiResource.prepare(peeler, session, method, targetUri, request.headers(),
                principalService.acquirePrincipal(request));
        } else {
            return AbstractResource.NOT_FOUND;
        }
    }
}
