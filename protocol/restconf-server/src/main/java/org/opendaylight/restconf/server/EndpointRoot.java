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
import java.net.URI;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * The root of resource hierarchy exposed from a particular endpoint.
 */
@NonNullByDefault
final class EndpointRoot {
    private final Map<String, ServerResource> resources;
    private final PrincipalService principalService;

    // FIXME: at some point we should just receive a Map of resources with coherent API
    EndpointRoot(final PrincipalService principalService, final Map<String, ServerResource> resources) {
        this.principalService = requireNonNull(principalService);
        this.resources = requireNonNull(resources);
    }

    PreparedRequest prepareRequest(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        final var peeler = new SegmentPeeler(targetUri);
        if (!peeler.hasNext()) {
            // We only support OPTIONS
            return method == ImplementedMethod.OPTIONS ? CompletedRequests.OK_OPTIONS
                : CompletedRequests.METHOD_NOT_ALLOWED_OPTIONS;
        }

        final var resource = resources.get(peeler.next());
        return switch (resource) {
            case null -> CompletedRequests.NOT_FOUND;
            case WellKnownResources wellKnown -> wellKnown.prepareRequest(peeler, method, headers);
            case RestconfServerResource restconf -> restconf.prepareRequest(peeler, session, method, targetUri, headers,
                principalService.acquirePrincipal(headers));
        };
    }
}
