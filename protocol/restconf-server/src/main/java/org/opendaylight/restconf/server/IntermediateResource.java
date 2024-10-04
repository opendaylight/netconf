/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Resource} which acts only as an intermediary step towards a resource.
 */
@NonNullByDefault
final class IntermediateResource extends Resource {
    private static final Logger LOG = LoggerFactory.getLogger(IntermediateResource.class);
    private static final CompletedRequest METHOD_NOT_ALLOWED;
    private static final CompletedRequest OK;

    static {
        final var headers = DefaultHttpHeadersFactory.headersFactory().newHeaders()
            .set(HttpHeaderNames.ALLOW, "OPTIONS");
        METHOD_NOT_ALLOWED = new DefaultCompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, headers);
        OK = new DefaultCompletedRequest(HttpResponseStatus.OK, headers);
    }

    private final String segment;
    private final Resource resource;

    IntermediateResource(final String segment, final Resource resource) {
        this.segment = requireNonNull(segment);
        this.resource = requireNonNull(resource);
    }

    @Override
    PreparedRequest prepare(final SegmentPeeler peeler, final TransportSession session, final ImplementedMethod method,
            final URI targetUri, final HttpHeaders headers, final @Nullable Principal principal) {
        if (!peeler.hasNext()) {
            return method == ImplementedMethod.OPTIONS ? OK : METHOD_NOT_ALLOWED;
        }

        final var next = peeler.next();
        if (segment.equals(next)) {
            return resource.prepare(peeler, session, method, targetUri, headers, principal);
        }

        LOG.debug("Resource for '{}' not found", next);
        return NOT_FOUND;
    }
}
