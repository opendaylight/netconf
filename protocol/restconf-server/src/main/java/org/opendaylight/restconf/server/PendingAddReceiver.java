/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EventStreamResponse;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.RestconfStream.Subscription;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * A pending request to add a receiver to a subscription.
 */
@NonNullByDefault
final class PendingAddReceiver extends PendingRequestWithoutBody<Registration> {
    private final Subscription subscription;
    private final ChannelSender sender;
    private final int sseHeartbeatIntervalMillis;

    PendingAddReceiver(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final Subscription subscription, final ChannelSender sender,
            final int sseHeartbeatIntervalMillis) {
        super(invariants, session, targetUri, principal);
        this.subscription = requireNonNull(subscription);
        this.sender = requireNonNull(sender);
        this.sseHeartbeatIntervalMillis = sseHeartbeatIntervalMillis;
    }

    @Override
    void execute(final NettyServerRequest<Registration> request) {
        subscription.addReceiver(request, sender);
    }

    @Override
    EventStreamResponse transformResult(final NettyServerRequest<?> request, final Registration result) {
        sender.enable(result);
        return new EventStreamResponse(HttpResponseStatus.OK, sender, sseHeartbeatIntervalMillis);
    }
}
