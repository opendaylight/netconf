/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RFC8650-compliant access to RFC8639 notification subscriptions.
 */
@NonNullByDefault
final class SubscriptionsResource extends AbstractEventStreamResource {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionsResource.class);

    SubscriptionsResource(final EndpointInvariants invariants, final RestconfStream.Registry streamRegistry,
            final int sseHeartbeatIntervalMillis, final int sseMaximumFragmentLength) {
        super(invariants, streamRegistry, sseHeartbeatIntervalMillis, sseMaximumFragmentLength);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String firstSegment,
            final SegmentPeeler peeler) {
        // Subscriptions do not have nested resources
        if (peeler.hasNext()) {
            return new EmptyResponse(HttpResponseStatus.NOT_FOUND);
        }

        final Uint32 subscriptionId;
        try {
            subscriptionId = Uint32.valueOf(firstSegment);
        } catch (IllegalArgumentException e) {
            LOG.debug("Invalid subscription id {}", firstSegment, e);
            return new EmptyResponse(HttpResponseStatus.NOT_FOUND);
        }

        final var subscription = streamRegistry.lookupSubscription(subscriptionId);
        if (subscription == null) {
            LOG.debug("Subscription for id {} not found", subscriptionId);
            return EmptyResponse.NOT_FOUND;
        }

        return switch (method) {
            case GET -> headers.contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)
                ? new PendingAddReceiver(invariants, session, targetUri, principal, subscription)
                : new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
            case HEAD -> EVENT_STREAM_HEAD;
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }
}
