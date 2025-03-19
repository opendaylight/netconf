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
            subscription.channelClosed(new Request<Empty>() {
                @Override
                public UUID uuid() {
                    return null;
                }

                @Override
                public @Nullable Principal principal() {
                    return null;
                }

                @Override
                public <I> Request<I> transform(Function<I, Empty> function) {
                    return null;
                }

                @Override
                public void completeWith(Empty result) {

                }

                @Override
                public void completeWith(RequestException failure) {

                }
            });
        } else {
            LOG.debug("Subscription id:{} already in END state during attempt to end it", id);
        }
    }
}
