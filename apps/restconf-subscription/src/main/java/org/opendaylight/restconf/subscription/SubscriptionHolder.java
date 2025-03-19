/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.Request;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class SubscriptionHolder extends AbstractRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHolder.class);

    private final Uint32 id;
    private final SubscriptionStateMachine stateMachine;
    private final RestconfStream.Registry streamRegistry;

    SubscriptionHolder(final Uint32 id,
            final SubscriptionStateMachine stateMachine, final RestconfStream.Registry streamRegistry) {
        this.id = requireNonNull(id);
        this.stateMachine = requireNonNull(stateMachine);
        this.streamRegistry =  requireNonNull(streamRegistry);
    }

    @Override
    protected void removeRegistration() {
        final var subscription = streamRegistry.lookupSubscription(id);
        if (subscription == null) {
            // subscription is no longer registered, it was terminated from elsewhere
            return;
        }
        final var state = stateMachine.lookupSubscriptionState(id);

        if (state == null) {
            LOG.warn("No subscription with ID:{}", id);
        } else if (state != SubscriptionState.END) {
            stateMachine.moveTo(id, SubscriptionState.END);
            subscription.channelClosed(null);
        } else {
            LOG.debug("Subscription id:{} already in END state during attempt to end it", id);
        }
    }
}
