/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.SubscriptionState;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(service = SubscriptionStateMachine.class)
public class SubscriptionStateMachine {
    /**
     * Internal helper DTO that not meant to be used anywhere else. Used to increase readability of the code and allow
     * us store subscription session and state in one map.
     */
    @NonNullByDefault
    private record SessionStatePair(TransportSession session, SubscriptionState state) {
        SessionStatePair {
            requireNonNull(session);
            requireNonNull(state);
        }

        SessionStatePair withState(final Uint32 id, final SubscriptionState newState) {
            return switch (state) {
                case START, SUSPENDED -> switch (newState) {
                    case START, SUSPENDED -> throw reject(id, newState);
                    case ACTIVE, END -> accept(id, newState);
                };
                case ACTIVE -> switch (newState) {
                    case START, ACTIVE -> throw reject(id, newState);
                    case SUSPENDED, END -> accept(id, newState);
                };
                case END -> throw reject(id, newState);
            };
        }

        private SessionStatePair accept(final Uint32 id, final SubscriptionState newState) {
            LOG.debug("Subscription {} moving from {} to {}", id, state, newState);
            return new SessionStatePair(session, newState);
        }

        private IllegalStateException reject(final Uint32 id, final SubscriptionState newState) {
            return new IllegalStateException(
                "Subscription %s cannot transition from %s to %s".formatted(id, state, newState));
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionStateMachine.class);

    private final ConcurrentMap<Uint32, SessionStatePair> subscriptionStateMap = new ConcurrentHashMap<>();

    @Inject
    @Activate
    public SubscriptionStateMachine() {
        LOG.debug("SubscriptionStateMachine initialized");
    }

    /**
     * Register new subscription with default {@link SubscriptionState#START} state.
     *
     * @param session session on which we are registering the subscription
     * @param subscriptionId id of the newly registered subscription
     */
    @NonNullByDefault
    public void registerSubscription(final TransportSession session, final Uint32 subscriptionId) {
        subscriptionStateMap.put(subscriptionId, new SessionStatePair(session, SubscriptionState.START));
    }

    /**
     * Moves subscription from its current state to new one assuming this transition is legal.
     *
     * @param subscriptionId id of the subscription
     * @param newState new state assigned to subscription
     * @throws NoSuchElementException if the subscription is not found
     * @throws IllegalStateException if transition to a new state is not legal
     */
    @NonNullByDefault
    public void moveTo(final Uint32 subscriptionId, final SubscriptionState newState) {
        requireNonNull(newState);
        // atomic search-check-and-replace, since we normally produce non-null, a null return indicates the mapping was
        // not present
        final var found = subscriptionStateMap.computeIfPresent(subscriptionId,
            (id, pair) -> pair.withState(id, newState));
        if (found == null) {
            throw new NoSuchElementException("No subscription " + subscriptionId);
        }
    }

    /**
     * Retrieves state of given subscription, if present.
     *
     * @param subscriptionId id of the subscription
     * @return current state of subscription, or {@code null}
     */
    public @Nullable SubscriptionState lookupSubscriptionState(final @NonNull Uint32 subscriptionId) {
        final var pair = subscriptionStateMap.get(subscriptionId);
        return pair != null ? pair.state : null;
    }

    /**
     * Retrieves session of given subscription, if present.
     *
     * @param subscriptionId id of the subscription
     * @return session tied to the subscription, or {@code null}
     */
    public @Nullable TransportSession lookupSubscriptionSession(final @NonNull Uint32 subscriptionId) {
        final var pair = subscriptionStateMap.get(subscriptionId);
        return pair != null ? pair.session : null;
    }
}
