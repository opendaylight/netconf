/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class SubscriptionHolder extends AbstractRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHolder.class);

    private final Uint32 id;
    private final RestconfStream.Registry streamRegistry;

    SubscriptionHolder(final Uint32 id, final RestconfStream.Registry streamRegistry) {
        this.id = requireNonNull(id);
        this.streamRegistry = requireNonNull(streamRegistry);
    }

    @Override
    protected void removeRegistration() {
        final var subscription = streamRegistry.lookupSubscription(id);
        if (subscription == null) {
            // subscription is no longer registered, it was terminated from elsewhere
            return;
        }
        if (subscription.state() != RestconfStream.SubscriptionState.END) {
            subscription.setState(RestconfStream.SubscriptionState.END);
            subscription.channelClosed();
        } else {
            LOG.debug("Subscription id:{} already in END state during attempt to end it", id);
            subscription.terminate(null, null);

        }
    }
}
