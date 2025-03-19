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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class SubscriptionHolder extends AbstractRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHolder.class);

    private final RestconfStream.Subscription subscription;
    private final SubscriptionStateMachine stateMachine;

    SubscriptionHolder(final RestconfStream.Subscription subscription, final SubscriptionStateMachine stateMachine) {
        this.subscription = requireNonNull(subscription);
        this.stateMachine = requireNonNull(stateMachine);
    }

    @Override
    protected void removeRegistration() {
        final var id = subscription.id();
        final var state = stateMachine.lookupSubscriptionState(id);

        if (state == null) {
            LOG.warn("No subscription with ID:{}", id);
        } else if (state != SubscriptionState.END) {
            stateMachine.moveTo(id, SubscriptionState.END);
        } else {
            LOG.debug("Subscription id:{} already in END state during attempt to end it", id);
        }

        subscription.channelClosed();
    }
}
