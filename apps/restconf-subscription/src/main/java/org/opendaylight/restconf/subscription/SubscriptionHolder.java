/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.NoSuchSubscription;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class SubscriptionHolder extends AbstractRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHolder.class);

    private final Uint32 id;
    private final SubscriptionStateService subscriptionStateService;
    private final RestconfStream.Registry streamRegistry;

    SubscriptionHolder(final Uint32 id, final SubscriptionStateService subscriptionStateService,
            final RestconfStream.Registry streamRegistry) {
        this.id = requireNonNull(id);
        this.subscriptionStateService = requireNonNull(subscriptionStateService);
        this.streamRegistry =  requireNonNull(streamRegistry);
    }

    @Override
    protected void removeRegistration() {
        final var subscription = streamRegistry.lookupSubscription(id);
        if (subscription == null) {
            // subscription is no longer registered, it was terminated from elsewhere
            return;
        }

        try {
            // FIXME: proper arguments
            subscription.terminate(null, null);
        } finally {
            try {
                subscriptionStateService.subscriptionTerminated(Instant.now(), id, NoSuchSubscription.QNAME);
            } catch (InterruptedException e) {
                LOG.warn("Could not send subscription terminated notification", e);
            }
        }
    }
}
