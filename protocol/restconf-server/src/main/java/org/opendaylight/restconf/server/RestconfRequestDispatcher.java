/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: rename to APIResource
final class RestconfRequestDispatcher extends AbstractResource {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);

    private final Map<String, AbstractResource> resources;
    private final @NonNull PrincipalService principalService;
    private final @NonNull String firstSegment;
    private final @NonNull List<String> otherSegments;

    RestconfRequestDispatcher(final RestconfServer server, final PrincipalService principalService,
            final List<String> segments, final String restconfPath, final ErrorTagMapping errorTagMapping,
            final MessageEncoding defaultEncoding, final PrettyPrintParam defaultPrettyPrint) {
        super(new EndpointInvariants(server, defaultPrettyPrint, errorTagMapping, defaultEncoding,
            URI.create(requireNonNull(restconfPath))));
        this.principalService = requireNonNull(principalService);

        firstSegment = segments.getFirst();
        otherSegments = segments.stream().skip(1).collect(Collectors.toUnmodifiableList());

        resources = Map.of(
            "data", new DataResource(invariants),
            "operations", new OperationsResource(invariants),
            "yang-library-version", new YLVResource(invariants),
            "modules", new ModulesResource(invariants));
    }

    @Override
    PreparedRequest prepare(final SegmentPeeler peeler, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final Principal principal) {
        LOG.debug("Preparing {} {}", method, targetUri);

        // peel all other segments out
        for (var segment : otherSegments) {
            if (!peeler.hasNext() || !segment.equals(peeler.next())) {
                return NOT_FOUND;
            }
        }

        if (!peeler.hasNext()) {
            // FIXME: we are rejecting requests to '{+restconf}', which matches JAX-RS server behaviour, but is not
            //        correct: we should be reporting the entire API Resource, as described in
            //        https://www.rfc-editor.org/rfc/rfc8040#section-3.3
            LOG.debug("Not servicing root request");
            return NOT_FOUND;
        }

        final var segment = peeler.next();
        final var resource = resources.get(segment);
        if (resource != null) {
            return resource.prepare(peeler, method, targetUri, headers, principal);
        }

        LOG.debug("Resource for '{}' not found", segment);
        return NOT_FOUND;
    }

    String firstSegment() {
        return firstSegment;
    }

    @NonNullByDefault
    void dispatch(final SegmentPeeler peeler, final ImplementedMethod method, final URI targetUri,
            final FullHttpRequest request, final RestconfRequest callback) {
        final var version = request.protocolVersion();

        switch (prepare(peeler, method, targetUri, request.headers(), principalService.acquirePrincipal(request))) {
            case CompletedRequest completed -> callback.onSuccess(completed.toHttpResponse(version));
            case PendingRequest<?> pending -> {
                LOG.debug("Dispatching {} {}", method, targetUri);
                callback.execute(pending, version, request.content());
            }
        }
    }
}
