/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTCONF API resource, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3>RFC8040, section 3.3</a>.
 */
final class APIResource extends AbstractResource {
    private static final Logger LOG = LoggerFactory.getLogger(APIResource.class);

    private final Map<String, AbstractResource> resources;
    private final List<String> otherSegments;

    @NonNullByDefault
    APIResource(final RestconfServer server, final List<String> otherSegments, final String restconfPath,
            final ErrorTagMapping errorTagMapping, final MessageEncoding defaultEncoding,
            final PrettyPrintParam defaultPrettyPrint) {
        super(new EndpointInvariants(server, defaultPrettyPrint, errorTagMapping, defaultEncoding,
            URI.create(requireNonNull(restconfPath))));
        this.otherSegments = requireNonNull(otherSegments);

        resources = Map.of(
            "data", new DataResource(invariants),
            "operations", new OperationsResource(invariants),
            "yang-library-version", new YLVResource(invariants),
            "modules", new ModulesResource(invariants));
    }

    @Override
    PreparedRequest prepare(final SegmentPeeler peeler, final TransportSession session, final ImplementedMethod method,
            final URI targetUri, final HttpHeaders headers, final Principal principal) {
        LOG.debug("Preparing {} {}", method, targetUri);

        // peel all other segments out
        for (var segment : otherSegments) {
            if (!peeler.hasNext() || !segment.equals(peeler.next())) {
                return NOT_FOUND;
            }
        }

        if (!peeler.hasNext()) {
            return prepare(session, method, targetUri, headers, principal);
        }

        final var segment = peeler.next();
        final var resource = resources.get(segment);
        if (resource != null) {
            return resource.prepare(peeler, session, method, targetUri, headers, principal);
        }

        LOG.debug("Resource for '{}' not found", segment);
        return NOT_FOUND;
    }

    // FIXME: we are rejecting requests to '{+restconf}', which matches JAX-RS server behaviour, but is not correct:
    //        we should be reporting the entire API Resource, as described in
    //        https://www.rfc-editor.org/rfc/rfc8040#section-3.3
    @NonNullByDefault
    private static PreparedRequest prepare(final TransportSession session, final ImplementedMethod method,
            final URI targetUri, final HttpHeaders headers, final @Nullable Principal principal) {
        LOG.debug("Not servicing root request");
        return NOT_FOUND;
    }
}
