/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.RestconfStream;

/**
 * An {@link AbstractLeafResource} which provides access to a number of HTTP event streams.
 */
@NonNullByDefault
abstract sealed class AbstractEventStreamResource extends AbstractLeafResource
        permits StreamsResource, SubscriptionsResource {
    static final HeadersResponse EVENT_STREAM_HEAD = HeadersResponse.of(HttpResponseStatus.OK,
        HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM);

    final RestconfStream.Registry streamRegistry;
    final int sseHeartbeatIntervalMillis;
    final int sseMaximumFragmentLength;

    AbstractEventStreamResource(final EndpointInvariants invariants, final RestconfStream.Registry streamRegistry,
            final int sseHeartbeatIntervalMillis, final int sseMaximumFragmentLength) {
        super(invariants);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.sseHeartbeatIntervalMillis = sseHeartbeatIntervalMillis;
        this.sseMaximumFragmentLength = sseMaximumFragmentLength;
    }

    @Override
    final PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        final var peeler = new SegmentPeeler(path);
        if (!peeler.hasNext()) {
            return method == ImplementedMethod.OPTIONS ? OPTIONS_ONLY_OK : OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        }

        return prepare(session, method, targetUri, headers, principal, peeler.next(), peeler);
    }

    abstract PreparedRequest prepare(TransportSession session, ImplementedMethod method, URI targetUri,
        HttpHeaders headers, @Nullable Principal principal, String firstSegment, SegmentPeeler peeler);
}
